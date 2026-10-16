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

    public static PlotContents of(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return PlotContents.EMPTY;
        long now = subLevel.getLevel().getGameTime();
        Cached cached = CACHE.get(subLevel.getUniqueId());
        if (cached != null && now <= cached.until()) return cached.contents();

        PlotContents contents = scan(subLevel);
        CACHE.put(subLevel.getUniqueId(), new Cached(contents, now + CACHE_TICKS));
        return contents;
    }

    public static boolean isNoShrink(BlockState state) {
        return state != null && (state.is(NO_SHRINK) || state.is(OLD_NO_SHRINK) || configured.contains(state.getBlock()));
    }

    public static int shrunkBlockLimit() {
        return shrunkBlockLimit;
    }

    public static void forget(UUID id) {
        if (id != null) CACHE.remove(id);
    }

    public static void configure(Set<Block> noShrink, int limit) {
        configured = Set.copyOf(noShrink);
        shrunkBlockLimit = Math.max(1, limit);
        CACHE.clear();
    }

    static String refuseShrink(ServerSubLevel subLevel, double from, double to) {
        if (to >= 1.0D || to >= from) return null;
        PlotContents contents = of(subLevel);
        if (contents.hasNoShrinkBlock()) return "Cannot shrink: contains " + contents.noShrink();
        if (contents.blocks() > shrunkBlockLimit) {
            return "Too many blocks to shrink: " + contents.blocks() + " (limit " + shrunkBlockLimit + ")";
        }
        return null;
    }

    private static PlotContents scan(ServerSubLevel subLevel) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        int blocks = 0;
        int blockEntities = 0;
        BlockPos noShrinkPos = null;
        ResourceLocation noShrink = null;

        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            ChunkPos chunkPos = chunk.getPos();
            int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) continue;

            LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;
                int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                int minY = Math.max(bounds.minY(), sectionMinY);
                int maxY = Math.min(bounds.maxY(), sectionMinY + 15);

                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
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

            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ
                        || pos.getY() < bounds.minY() || pos.getY() > bounds.maxY()) continue;
                if (!chunk.getBlockState(pos).isAir()) blockEntities++;
            }
        }
        return new PlotContents(blocks, blockEntities, noShrinkPos, noShrink);
    }

    private static TagKey<Block> tag(String namespace) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, "noshrink"));
    }

    private record Cached(PlotContents contents, long until) {}

    private PlotScan() {}
}
