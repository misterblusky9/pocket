package com.misterblusky9.pym.internal.collision;

import com.misterblusky9.pym.api.SubLevelShape;
import com.misterblusky9.pym.api.client.DebugOverlay;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CollisionTrace {
    private static final int MAX_BOXES = 32768;
    private static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(Frame::new);
    private static volatile boolean enabled;

    public static boolean enabled() {
        return enabled;
    }

    public static void begin(Entity entity, SubLevel subLevel) {
        if (!(entity instanceof Player player) || !player.isLocalPlayer()) return;
        Frame frame = FRAME.get();
        enabled = DebugOverlay.showingPlayerLod();
        if (!enabled) {
            frame.clear();
            return;
        }
        Level level = entity.level();
        long tick = level.getGameTime();
        if (frame.level != level || frame.tick != tick) {
            frame.clear();
            frame.level = level;
            frame.tick = tick;
        }
        frame.selected = subLevel == null ? null : subLevel.getUniqueId();
    }

    public static void record(Entity entity, BlockState state, BlockPos pos, VoxelShape shape, int lod) {
        if (!enabled || !(entity instanceof Player player) || !player.isLocalPlayer()
                || (lod == 0 && SubLevelIndexedCollision.pendingExactFallback())) return;
        Frame frame = FRAME.get();
        if (frame.level != entity.level() || frame.selected == null
                || state.isAir() || shape.isEmpty() || frame.overflow.contains(frame.selected)) return;
        frame.snapshots.remove(frame.selected);
        Map<SubLevelShape.Box, Integer> boxes = frame.boxes.computeIfAbsent(frame.selected, unused -> new HashMap<>());
        for (AABB box : shape.toAabbs()) {
            if (boxes.size() >= MAX_BOXES) {
                frame.boxes.remove(frame.selected);
                frame.overflow.add(frame.selected);
                return;
            }
            boxes.put(new SubLevelShape.Box(pos.getX() + box.minX, pos.getY() + box.minY,
                    pos.getZ() + box.minZ, pos.getX() + box.maxX,
                    pos.getY() + box.maxY, pos.getZ() + box.maxZ), lod);
        }
    }

    public static Map<SubLevelShape.Box, Integer> outlines(SubLevel subLevel, Level level) {
        Frame frame = FRAME.get();
        long age = level.getGameTime() - frame.tick;
        if (frame.level != level || age < 0 || age > 2) return Map.of();
        return frame.snapshots.computeIfAbsent(subLevel.getUniqueId(), id -> {
            Map<SubLevelShape.Box, Integer> boxes = frame.boxes.get(id);
            return boxes == null ? Map.of() : Map.copyOf(boxes);
        });
    }

    private static final class Frame {
        Level level;
        long tick = Long.MIN_VALUE;
        UUID selected;
        final Map<UUID, Map<SubLevelShape.Box, Integer>> boxes = new HashMap<>();
        final Set<UUID> overflow = new HashSet<>();
        final Map<UUID, Map<SubLevelShape.Box, Integer>> snapshots = new HashMap<>();

        void clear() {
            level = null;
            tick = Long.MIN_VALUE;
            selected = null;
            boxes.clear();
            overflow.clear();
            snapshots.clear();
        }
    }

    private CollisionTrace() {}
}
