package com.misterblusky9.pym.api;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Plots {
    public static boolean inBounds(SubLevelContainer container, int x, int z) {
        int side = 1 << container.getLogSideLength();
        return x >= 0 && z >= 0 && x < side && z < side;
    }

    public static boolean isFree(SubLevelContainer container, int x, int z) {
        return inBounds(container, x, z) && container.getSubLevel(x, z) == null
                && !container.getOccupancy().get(container.getIndex(x, z));
    }

    public static int[] free(SubLevelContainer container, int preferX, int preferZ) {
        if (isFree(container, preferX, preferZ)) return new int[] {preferX, preferZ};
        int side = 1 << container.getLogSideLength();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                if (isFree(container, x, z)) return new int[] {x, z};
            }
        }
        return null;
    }

    public static Vec3i delta(ServerLevel from, int fromX, int fromZ, ServerLevel to, int toX, int toZ) {
        SubLevelContainer source = SubLevelContainer.getContainer(from);
        SubLevelContainer target = SubLevelContainer.getContainer(to);
        int size = 1 << (target.getLogPlotSize() + 4);
        return new Vec3i(
                (target.getOrigin().x + toX - source.getOrigin().x - fromX) * size, 0,
                (target.getOrigin().y + toZ - source.getOrigin().y - fromZ) * size);
    }

    public static int[] rebase(CompoundTag plot, ServerLevel from, ServerLevel to, int toX, int toZ) {
        Vec3i delta = delta(from, plot.getInt("plot_x"), plot.getInt("plot_z"), to, toX, toZ);
        int sectionShift = from.getMinSection() - to.getMinSection();
        plot.putInt("plot_x", toX);
        plot.putInt("plot_z", toZ);
        int blockEntities = 0;
        int ticks = 0;
        CompoundTag chunks = plot.getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(key);
            if (sectionShift != 0) chunk.put("sections", restack(chunk.getCompound("sections"), sectionShift,
                    to.getSectionsCount()));
            blockEntities += shift(chunk.getList("block_entities", Tag.TAG_COMPOUND), delta);
            ticks += shift(chunk.getList("block_ticks", Tag.TAG_COMPOUND), delta);
            ticks += shift(chunk.getList("fluid_ticks", Tag.TAG_COMPOUND), delta);
        }
        return new int[] {blockEntities, ticks};
    }

    private static CompoundTag restack(CompoundTag sections, int shift, int count) {
        CompoundTag moved = new CompoundTag();
        for (String key : sections.getAllKeys()) {
            int index = Integer.parseInt(key) + shift;
            if (index >= 0 && index < count) moved.put(Integer.toString(index), sections.get(key));
        }
        return moved;
    }

    private static int shift(ListTag entries, Vec3i delta) {
        int shifted = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.contains("x", Tag.TAG_ANY_NUMERIC) || !entry.contains("y", Tag.TAG_ANY_NUMERIC)
                    || !entry.contains("z", Tag.TAG_ANY_NUMERIC)) continue;
            entry.putInt("x", entry.getInt("x") + delta.getX());
            entry.putInt("y", entry.getInt("y") + delta.getY());
            entry.putInt("z", entry.getInt("z") + delta.getZ());
            shifted++;
        }
        return shifted;
    }

    public static @Nullable Vec3i move(CompoundTag plot, ServerLevel from, ServerLevel to) {
        ServerSubLevelContainer target = SubLevelContainer.getContainer(to);
        if (target == null || SubLevelContainer.getContainer(from) == null
                || plot.getInt("log_size") != target.getLogPlotSize()) return null;
        int fromX = plot.getInt("plot_x");
        int fromZ = plot.getInt("plot_z");
        int[] slot = free(target, fromX, fromZ);
        if (slot == null) return null;
        Vec3i delta = delta(from, fromX, fromZ, to, slot[0], slot[1]);
        if (!delta.equals(Vec3i.ZERO) || from.getMinSection() != to.getMinSection())
            rebase(plot, from, to, slot[0], slot[1]);
        return delta;
    }

    public static List<List<BlockPos>> components(Collection<BlockPos> blocks) {
        Set<BlockPos> remaining = new HashSet<>(blocks);
        List<List<BlockPos>> parts = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : blocks) {
            if (!remaining.remove(start)) continue;
            List<BlockPos> part = new ArrayList<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                part.add(pos);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (distance == 0 || distance == 3) continue;
                            BlockPos neighbor = pos.offset(dx, dy, dz);
                            if (remaining.remove(neighbor)) queue.addLast(neighbor);
                        }
                    }
                }
            }
            parts.add(part);
        }
        return parts;
    }

    private Plots() {}
}
