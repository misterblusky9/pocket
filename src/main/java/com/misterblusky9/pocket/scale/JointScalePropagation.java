package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ConstraintRefresh;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class JointScalePropagation {
    private static boolean propagating;

    public static void onCommanded(final ServerSubLevel subLevel, final CompressionStage stage) {
        if (propagating) return;
        if (subLevel == null || stage == null || subLevel.isRemoved()) return;

        final UUID origin = subLevel.getUniqueId();
        if (origin == null) return;

        if (SubLevelParentage.parentOf(origin) != null) return;

        final int delta = stage.depth() - commandedDepth(subLevel);
        if (delta == 0) return;

        final ServerSubLevelContainer container =
                ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return;

        propagating = true;
        try {
            spread(container, origin, delta);
        } finally {
            propagating = false;
        }
    }

    private static void spread(
            final ServerSubLevelContainer container, final UUID origin, final int delta
    ) {
        final Deque<UUID> queue = new ArrayDeque<>(ConstraintRefresh.neighbours(origin));
        final Set<UUID> seen = new HashSet<>(queue);
        seen.add(origin);

        while (!queue.isEmpty()) {
            final UUID id = queue.poll();
            if (id == null) continue;

            for (final UUID next : ConstraintRefresh.neighbours(id)) {
                if (seen.add(next)) queue.add(next);
            }

            if (!(container.getSubLevel(id) instanceof final ServerSubLevel attached)
                    || attached.isRemoved()) {
                continue;
            }

            if (SubLevelParentage.parentOf(id) != null) continue;

            if (CompressionSessions.isHeld(id)) continue;

            final int current = commandedDepth(attached);
            final CompressionStage goal = CompressionStage.fromDepth(current + delta);
            if (goal.depth() == current) continue;

            PocketTrace.scale(
                    "joint-propagating {} stages to attached craft {} (depth {} -> {})",
                    delta, id, current, goal.depth());

            ScaleController.forceStage(attached, goal, attached.getLevel().getGameTime());
        }
    }

    private static int commandedDepth(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (id == null || !ScaleState.hasServerState(id)) return CompressionStage.NORMAL.depth();

        final ScaleState.ServerState state = ScaleState.serverState(subLevel);
        if (state == null) return CompressionStage.NORMAL.depth();

        final CompressionStage commanded = state.transitionStage() != null
                ? state.transitionStage()
                : state.stableStage();
        return commanded == null ? CompressionStage.NORMAL.depth() : commanded.depth();
    }

    private JointScalePropagation() {}
}
