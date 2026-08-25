package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ScaledBoundsCollider;
import com.misterblusky9.pocket.physics.ScaledSweepGuard;
import com.misterblusky9.pocket.physics.ScaledVelocityGuard;
import com.misterblusky9.pocket.physics.ScaledFluidForces;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScaleLifecycle {
    private static final long ABSENCE_GRACE_NANOS = 10_000_000_000L;

    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();

    private static final Set<UUID> LIVE = new HashSet<>();

    public static synchronized void reapDeparted(final ServerSubLevelContainer container) {
        final long now = System.nanoTime();

        LIVE.clear();
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            final UUID id = subLevel.getUniqueId();
            if (id != null && !subLevel.isRemoved()) LIVE.add(id);
        }

        for (final UUID id : LIVE) LAST_SEEN.put(id, now);

        final Set<UUID> tracked = ScaleState.trackedIds();
        LAST_SEEN.keySet().retainAll(tracked);
        for (final UUID id : tracked) LAST_SEEN.putIfAbsent(id, now);

        final Iterator<Map.Entry<UUID, Long>> iterator = LAST_SEEN.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() < ABSENCE_GRACE_NANOS) continue;

            final UUID id = entry.getKey();
            PocketTrace.scale(
                    "releasing state for departed sub-level uuid={} unseenForMs={}",
                    id, (now - entry.getValue()) / 1_000_000L);

            ScaleState.clearServerState(id);
            ScaleState.clearServerBounds(id);
            ScaledBoundsCollider.forgetSubLevel(id);
            ScaledVelocityGuard.forget(id);
            ScaledSweepGuard.forget(id);
            ScaledFluidForces.forget(id);
            SubLevelParentage.forget(id);
            PocketMetrics.invalidate(id);
            iterator.remove();
        }
    }

    private ScaleLifecycle() {}
}
