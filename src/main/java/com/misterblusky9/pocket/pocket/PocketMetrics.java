package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionBlacklist;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record PocketMetrics(int blocks, int blockEntities) {
    private static final long CACHE_TICKS = 40L;
    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

    public static PocketMetrics measure(final ServerSubLevel subLevel) {
        return scan(subLevel, Integer.MAX_VALUE);
    }

    public static PocketMetrics measureForCompression(final ServerSubLevel subLevel, final long gameTime) {
        final UUID id = subLevel.getUniqueId();
        final Cached cached = CACHE.get(id);
        if (cached != null && gameTime <= cached.validUntil()) return cached.metrics();

        final PocketMetrics metrics = scan(subLevel, PocketSized.MAX_COMPRESSED_BLOCKS + 1);
        CACHE.put(id, new Cached(metrics, gameTime + CACHE_TICKS));
        return metrics;
    }

    public static void invalidate(final UUID id) {
        if (id != null) {
            CACHE.remove(id);
            CompressionBlacklist.invalidate(id);
        }
    }

    public static void adjustBlocks(final UUID id, final int delta, final long gameTime) {
        if (id == null || delta == 0) return;
        CompressionBlacklist.invalidate(id);
        CACHE.computeIfPresent(id, (ignored, cached) -> {
            final PocketMetrics old = cached.metrics();
            final int blocks = Math.max(0, old.blocks() + delta);
            return new Cached(new PocketMetrics(blocks, old.blockEntities()), gameTime + CACHE_TICKS);
        });
    }

    private static PocketMetrics scan(final ServerSubLevel subLevel, final int stopAfterBlocks) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        int blocks = 0;
        int blockEntities = 0;

        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos chunkPos = chunk.getPos();

            final int chunkMinX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            final int chunkMaxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            final int chunkMinZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            final int chunkMaxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (chunkMinX > chunkMaxX || chunkMinZ > chunkMaxZ) continue;

            final LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                final LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;

                final int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                final int minY = Math.max(bounds.minY(), sectionMinY);
                final int maxY = Math.min(bounds.maxY(), sectionMinY + 15);
                if (minY > maxY) continue;

                for (int y = minY; y <= maxY; y++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.isAir()) continue;
                            blocks++;
                            if (state.hasBlockEntity()) blockEntities++;
                            if (blocks >= stopAfterBlocks) {
                                return new PocketMetrics(blocks, blockEntities);
                            }
                        }
                    }
                }
            }
        }

        return new PocketMetrics(blocks, blockEntities);
    }

    private record Cached(PocketMetrics metrics, long validUntil) {}
}
