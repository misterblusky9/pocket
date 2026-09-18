package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ConstraintRefresh;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JointScalePropagation {
    private static boolean propagating;

    public static boolean onCommanded(
            final ServerSubLevel subLevel,
            final double scale,
            final ScalePhysicsMode physicsMode,
            final ScaleLimits limits
    ) {
        if (propagating) return true;
        final Map<ServerSubLevel, Double> goals = plan(subLevel, scale);
        if (goals.isEmpty()) return true;
        if (!permitted(goals, limits)) return false;

        propagating = true;
        try {
            for (final Map.Entry<ServerSubLevel, Double> entry : goals.entrySet()) {
                final ServerSubLevel attached = entry.getKey();
                PocketTrace.scale(
                        "joint-propagating to attached craft {} ({} -> {})",
                        attached.getUniqueId(), commandedScale(attached), entry.getValue());

                ScaleController.forceScale(
                        attached, entry.getValue(), attached.getLevel().getGameTime(), null, true, physicsMode, limits);
            }
        } finally {
            propagating = false;
        }
        return true;
    }

    public static boolean permits(final ServerSubLevel subLevel, final double scale, final ScaleLimits limits) {
        return propagating || permitted(plan(subLevel, scale), limits);
    }

    private static boolean permitted(final Map<ServerSubLevel, Double> goals, final ScaleLimits limits) {
        for (final Map.Entry<ServerSubLevel, Double> entry : goals.entrySet()) {
            if (!limits.permits(commandedScale(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    private static Map<ServerSubLevel, Double> plan(final ServerSubLevel subLevel, final double scale) {
        final Map<ServerSubLevel, Double> goals = new LinkedHashMap<>();
        if (subLevel == null || !PocketSized.isValidScale(scale) || subLevel.isRemoved()) return goals;

        final UUID origin = subLevel.getUniqueId();
        if (origin == null) return goals;

        if (SubLevelParentage.parentOf(origin) != null) return goals;

        final double commanded = commandedScale(subLevel);
        if (ScaleController.sameScale(scale, commanded)) return goals;
        final double ratio = scale / commanded;

        final ServerSubLevelContainer container =
                ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return goals;

        final Deque<UUID> queue = new ArrayDeque<>(ConstraintRefresh.neighbours(origin));
        final Set<UUID> seen = new HashSet<>(queue);
        seen.add(origin);

        while (!queue.isEmpty()) {
            final UUID id = queue.poll();
            if (id == null) continue;

            if (!(container.getSubLevel(id) instanceof final ServerSubLevel attached)
                    || attached.isRemoved()) {
                continue;
            }

            for (final UUID next : ConstraintRefresh.neighbours(id)) {
                if (seen.add(next)) queue.add(next);
            }

            if (SubLevelParentage.parentOf(id) != null) continue;

            if (CompressionSessions.isHeld(id)) continue;

            final double current = commandedScale(attached);
            final double goal = CompressionStage.snap(current * ratio);
            if (ScaleController.sameScale(goal, current)) continue;
            goals.put(attached, goal);
        }
        return goals;
    }

    private static double commandedScale(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (id == null || !ScaleState.hasServerState(id)) return ScaleState.getServerScale(subLevel);
        return ScaleState.serverState(subLevel).goalScale();
    }

    private JointScalePropagation() {}
}
