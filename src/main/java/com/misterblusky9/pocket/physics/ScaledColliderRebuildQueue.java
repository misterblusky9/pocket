package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScaledColliderRebuildQueue {
    private static final Map<SubLevelContainer, LinkedHashMap<UUID, ServerSubLevel>> DIRTY = new IdentityHashMap<>();

    public static boolean mark(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;
        final SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return false;
        synchronized (DIRTY) {
            return DIRTY.computeIfAbsent(container, ignored -> new LinkedHashMap<>())
                    .put(subLevel.getUniqueId(), subLevel) == null;
        }
    }

    public static void flush(final ServerSubLevelContainer container) {
        final List<ServerSubLevel> pending;
        synchronized (DIRTY) {
            final Map<UUID, ServerSubLevel> dirty = DIRTY.remove(container);
            if (dirty == null || dirty.isEmpty()) return;
            pending = new ArrayList<>(dirty.values());
        }
        for (final ServerSubLevel subLevel : pending) {
            if (subLevel == null || subLevel.isRemoved() || !ScaleState.isScaled(subLevel)) continue;
            ScaledBoundsCollider.rebuildInPlace(subLevel);
        }
    }

    private ScaledColliderRebuildQueue() {}
}
