package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.Pym;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlotScan {
    public static final TagKey<Block> NO_SHRINK = tag(Pym.MOD_ID);
    private static final String LEGACY_NAMESPACE = "pocket";
    private static final TagKey<Block> OLD_NO_SHRINK = tag(LEGACY_NAMESPACE);

    private static final long CACHE_TICKS = 40L;
    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

    private static volatile Set<Block> configured = Set.of();
    private static volatile int shrunkBlockLimit = Integer.MAX_VALUE;

    public static PlotContents of(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return PlotContents.EMPTY;
        final long now = subLevel.getLevel().getGameTime();
        final Cached cached = CACHE.get(subLevel.getUniqueId());
        if (cached != null && now <= cached.until()) return cached.contents();

        final PlotContents contents = scan(subLevel);
        CACHE.put(subLevel.getUniqueId(), new Cached(contents, now + CACHE_TICKS));
        return contents;
    }

    public static boolean isNoShrink(final BlockState state) {
        return state != null && (state.is(NO_SHRINK) || state.is(OLD_NO_SHRINK) || configured.contains(state.getBlock()));
    }

    public static int shrunkBlockLimit() {
        return shrunkBlockLimit;
    }

    public static void forget(final UUID id) {
        if (id != null) CACHE.remove(id);
    }

    public static void configure(final Set<Block> noShrink, final int limit) {
        configured = Set.copyOf(noShrink);
        shrunkBlockLimit = Math.max(1, limit);
        CACHE.clear();
    }

    static String refuseShrink(final ServerSubLevel subLevel, final double from, final double to) {
        if (to >= 1.0D || to >= from) return null;
        final PlotContents contents = of(subLevel);
        if (contents.hasNoShrinkBlock()) return "Cannot shrink: contains " + contents.noShrink();
        if (contents.blocks() > shrunkBlockLimit) {
            return "Too many blocks to shrink: " + contents.blocks() + " (limit " + shrunkBlockLimit + ")";
        }
        return null;
    }

    private static PlotContents scan(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        int blocks = 0;
        int blockEntities = 0;
        BlockPos noShrinkPos = null;
        ResourceLocation noShrink = null;

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

                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.isAir()) continue;
                            blocks++;
                            if (noShrink == null && isNoShrink(state)) {
                                noShrinkPos = new BlockPos(x, y, z);
                                noShrink = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            }
                        }
                    }
                }
            }

            for (final BlockPos pos : chunk.getBlockEntities().keySet()) {
                if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ
                        || pos.getY() < bounds.minY() || pos.getY() > bounds.maxY()) continue;
                if (!chunk.getBlockState(pos).isAir()) blockEntities++;
            }
        }
        return new PlotContents(blocks, blockEntities, noShrinkPos, noShrink);
    }

    private static TagKey<Block> tag(final String namespace) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, "noshrink"));
    }

    private record Cached(PlotContents contents, long until) {}

    private PlotScan() {}
}
