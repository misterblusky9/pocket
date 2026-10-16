package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FacadeTiling;
import com.misterblusky9.pocket.mixin.create.VirtualRenderWorldAccessor;
import com.misterblusky9.pocket.mixin.create.WrappedBlockAndTintGetterAccessor;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CopycatFacadeFrames {
    public record Frame(double scale, int x, int y, int z) {
        public int along(Direction.Axis axis) {
            return axis.choose(this.x, this.y, this.z);
        }
    }

    private record Craft(UUID id, double scale, int[] offset) {}

    private record Preview(double scale, int x, int y, int z) {}

    private static final Map<Long, Craft> CRAFTS = new ConcurrentHashMap<>();
    private static final Map<Long, Set<Long>> SECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]> OFFSETS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Preview> PREVIEW = new ThreadLocal<>();
    private static volatile int plotShift = SubLevelContainer.DEFAULT_LOG_PLOT_SIZE + 4;

    public static Frame frameAt(BlockAndTintGetter world, BlockPos pos) {
        Preview preview = PREVIEW.get();
        if (preview != null) {
            return new Frame(preview.scale(), preview.x() + pos.getX(), preview.y() + pos.getY(), preview.z() + pos.getZ());
        }

        VirtualRenderWorldAccessor contraption = contraptionWorld(world);
        if (contraption != null) {
            BlockPos placed = pos.offset(contraption.pocket$getBiomeOffset());
            Craft craft = CRAFTS.get(plotKey(placed.getX(), placed.getZ()));
            return craft == null ? null : frameOf(craft, placed, contraption.pocket$getLevel().getMinBuildHeight());
        }

        long plot = plotKey(pos.getX(), pos.getZ());
        Craft craft = CRAFTS.get(plot);
        if (craft == null) {
            if (Sable.HELPER.getContainingClient(new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) != null) {
                track(plot, pos);
            }
            return null;
        }

        track(plot, pos);
        return frameOf(craft, pos, world.getMinBuildHeight());
    }

    public static VirtualRenderWorldAccessor contraptionWorld(BlockAndTintGetter world) {
        while (world instanceof WrappedBlockAndTintGetterAccessor wrapper) world = wrapper.pocket$getWrapped();
        return world instanceof VirtualRenderWorldAccessor contraption ? contraption : null;
    }

    private static Frame frameOf(Craft craft, BlockPos pos, int minBuildHeight) {
        int plotSize = 1 << plotShift;
        int[] shift = FacadeTiling.reduce(craft.offset(), craft.scale());
        return new Frame(craft.scale(),
                Math.floorMod(pos.getX(), plotSize) + shift[0],
                pos.getY() - minBuildHeight + shift[1],
                Math.floorMod(pos.getZ(), plotSize) + shift[2]);
    }

    public static void acceptAlignment(UUID craft, int[] offset) {
        if (craft != null && offset != null && offset.length == 3) OFFSETS.put(craft, offset.clone());
    }

    public static boolean previewing() {
        return PREVIEW.get() != null;
    }

    public static void inPreview(double scale, int[] anchor, Runnable render) {
        PREVIEW.set(new Preview(scale, anchor[0], anchor[1], anchor[2]));
        try {
            render.run();
        } finally {
            PREVIEW.remove();
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null) {
            CRAFTS.clear();
            SECTIONS.clear();
            OFFSETS.clear();
            return;
        }
        plotShift = container.getLogPlotSize() + 4;

        Set<Long> live = new HashSet<>();
        Set<UUID> liveIds = new HashSet<>();
        Set<UUID> gone = new HashSet<>();
        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (!(subLevel instanceof ClientSubLevel client) || client.isRemoved()) continue;
            ChunkPos chunk = client.getPlot().getChunkMin();
            long plot = plotKey(chunk.getMinBlockX(), chunk.getMinBlockZ());
            live.add(plot);
            liveIds.add(client.getUniqueId());

            double scale = ScaleBounds.clampValid(Pym.scale().of(client));
            int[] offset = OFFSETS.get(client.getUniqueId());
            Craft known = CRAFTS.get(plot);
            boolean fresh = known == null || !known.id().equals(client.getUniqueId());
            boolean rescaled = !fresh && Pym.scale().isSettled(client) && !ScaleBounds.same(known.scale(), scale);
            boolean realigned = !fresh && !Arrays.equals(known.offset(), offset);
            if (!fresh && !rescaled && !realigned) continue;

            if (known != null) gone.add(known.id());
            CRAFTS.put(plot, new Craft(client.getUniqueId(), fresh || rescaled ? scale : known.scale(), offset));
            Set<Long> sections = SECTIONS.get(plot);
            var renderData = client.getRenderData();
            if (sections == null || renderData == null) continue;
            for (long section : sections) {
                renderData.setDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section), false);
            }
        }
        CRAFTS.forEach((plot, craft) -> {
            if (!live.contains(plot)) gone.add(craft.id());
        });
        CRAFTS.keySet().retainAll(live);
        SECTIONS.keySet().retainAll(live);
        gone.removeAll(liveIds);
        OFFSETS.keySet().removeAll(gone);
    }

    public static void blockChanged(BlockPos pos) {
        long plot = plotKey(pos.getX(), pos.getZ());
        Craft craft = CRAFTS.get(plot);
        Set<Long> sections = SECTIONS.get(plot);
        if (craft == null || sections == null || craft.scale() >= ScaleBounds.FULL) return;

        var level = Minecraft.getInstance().level;
        SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(craft.id()) instanceof ClientSubLevel client)) return;
        var renderData = client.getRenderData();
        if (renderData == null) return;

        int reach = (int) Math.ceil(1.0D / craft.scale()) + 1;
        for (long section : sections) {
            if (near(SectionPos.x(section), pos.getX(), reach)
                    && near(SectionPos.y(section), pos.getY(), reach)
                    && near(SectionPos.z(section), pos.getZ(), reach)) {
                renderData.setDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section), false);
            }
        }
    }

    private static boolean near(int section, int block, int reach) {
        int min = SectionPos.sectionToBlockCoord(section);
        return block + reach >= min && block - reach <= min + 15;
    }

    private static void track(long plot, BlockPos pos) {
        SECTIONS.computeIfAbsent(plot, ignored -> ConcurrentHashMap.newKeySet()).add(SectionPos.asLong(pos));
    }

    private static long plotKey(int blockX, int blockZ) {
        return ChunkPos.asLong(blockX >> plotShift, blockZ >> plotShift);
    }

    private CopycatFacadeFrames() {}
}
