package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.config.PocketServerConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompressionBlacklist {
    public static final String MESSAGE = "Cannot shrink: contains restricted blocks";

    public static final TagKey<Block> TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "noshrink")
    );

    private static final long CACHE_TICKS = 40L;
    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();
    private static final Result ALLOWED = new Result(null, null);

    public static Result find(final ServerSubLevel subLevel, final long gameTime) {
        if (subLevel == null || subLevel.getUniqueId() == null) return ALLOWED;

        final UUID id = subLevel.getUniqueId();
        final Cached cached = CACHE.get(id);
        if (cached != null && gameTime <= cached.validUntil()) return cached.result();

        final Result result = scan(subLevel);
        CACHE.put(id, new Cached(result, gameTime + CACHE_TICKS));
        return result;
    }

    public static boolean isBlacklisted(final BlockState state) {
        return state != null
                && (state.is(TAG) || PocketServerConfig.isNoShrinkBlock(state.getBlock()));
    }

    public static ResourceLocation blockId(final BlockState state) {
        return state == null ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    public static void invalidate(final UUID id) {
        if (id != null) CACHE.remove(id);
    }

    public static void invalidateAll() {
        CACHE.clear();
    }

    private static Result scan(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();

        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos chunkPos = chunk.getPos();

            final int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            final int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            final int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            final int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) continue;

            final LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                final LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;

                final int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                final int minY = Math.max(bounds.minY(), sectionMinY);
                final int maxY = Math.min(bounds.maxY(), sectionMinY + 15);
                if (minY > maxY) continue;

                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (!isBlacklisted(state)) continue;
                            return new Result(new BlockPos(x, y, z), blockId(state));
                        }
                    }
                }
            }
        }

        return ALLOWED;
    }

    public record Result(BlockPos position, ResourceLocation blockId) {
        public boolean blocked() {
            return this.blockId != null;
        }

        public String message() {
            return this.blockId == null ? "" : MESSAGE;
        }
    }

    private record Cached(Result result, long validUntil) {}

    private CompressionBlacklist() {}
}
