package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScalePhysicsMode;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScalePhysicsTransitions {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        private ScalePhysicsMode mode = ScalePhysicsMode.TRACKING;
        private double envelopeFrom = 1.0D;
        private double envelopeTo = 1.0D;
        private boolean active;
        private boolean settling;
        private boolean pendingRestore;
    }

    public static void setMode(final ServerSubLevel subLevel, final ScalePhysicsMode mode) {
        if (subLevel == null || subLevel.getUniqueId() == null || mode == null) return;
        state(subLevel).mode = mode;
    }

    public static ScalePhysicsMode modeOf(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return ScalePhysicsMode.TRACKING;
        final State state = STATES.get(subLevel.getUniqueId());
        return state == null ? ScalePhysicsMode.TRACKING : state.mode;
    }

    public static boolean ownsScaledCollider(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;
        final State state = STATES.get(subLevel.getUniqueId());
        return ScaleState.isScaled(subLevel)
                || state != null && (state.active || state.settling || state.pendingRestore);
    }

    public static boolean usePrism(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;
        final State state = STATES.get(subLevel.getUniqueId());
        return state != null
                && state.mode == ScalePhysicsMode.FAST
                && state.active
                && !state.settling
                && ScaleState.isScaled(subLevel);
    }

    public static CompiledCollider.Bounds nativeBounds(
            final ServerSubLevel subLevel,
            final CompiledCollider.Bounds exact
    ) {
        return exact;
    }

    public static void drive(
            final ServerSubLevel subLevel,
            final double previousScale,
            final double currentScale,
            final double targetScale,
            final boolean scaleChanged,
            final boolean reachedTarget,
            final boolean structureBoundsChanged
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        if (!PocketSized.isValidScale(currentScale) || !PocketSized.isValidScale(targetScale)) return;

        final State state = state(subLevel);
        if (scaleChanged && (!state.active || Math.abs(state.envelopeTo - targetScale) > PocketSized.EPSILON)) {
            state.envelopeFrom = previousScale;
            state.envelopeTo = targetScale;
            state.active = true;
            state.settling = false;
            state.pendingRestore = false;
            ScaledBoundsCollider.applyScaledLocalBounds(subLevel);
        }

        if (structureBoundsChanged && ownsScaledCollider(subLevel)) {
            syncExternalStats(subLevel);
            ScaledColliderRebuildQueue.mark(subLevel);
        }

        if (scaleChanged && currentScale < 1.0D - PocketSized.EPSILON) {
            ScaledColliderRebuildQueue.mark(subLevel);
        }

        if (!reachedTarget) return;

        state.settling = true;
        if (targetScale >= 1.0D - PocketSized.EPSILON) {
            state.pendingRestore = true;
        } else {
            ScaledColliderRebuildQueue.mark(subLevel);
        }
    }

    public static void afterColliderFlush(final ServerSubLevelContainer container) {
        if (container == null || STATES.isEmpty()) return;
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) continue;
            final State state = STATES.get(subLevel.getUniqueId());
            if (state == null) continue;
            if (RapierBridge.bodyId(subLevel) == RapierBridge.NO_BODY) continue;

            if (state.pendingRestore) {
                state.pendingRestore = false;
                state.active = false;
                state.settling = false;
                state.envelopeFrom = 1.0D;
                state.envelopeTo = 1.0D;
                if (ScaledBoundsCollider.hasSynthetic(subLevel.getUniqueId())) {
                    ScaledBoundsCollider.restoreOriginal(subLevel);
                }
                RapierBridge.syncMassProperties(subLevel, 1.0D, false);
                ScaleState.captureServerBounds(subLevel);
                continue;
            }

            if (!state.settling) continue;
            final double targetScale = state.envelopeTo;
            final CompiledCollider current = ColliderCoordinator.current(subLevel.getUniqueId());
            if (current == null
                    || current.mode() != CompiledCollider.Mode.DETAILED
                    || Math.abs(current.scale() - targetScale) > PocketSized.EPSILON) {
                continue;
            }

            state.active = false;
            state.settling = false;
            state.envelopeFrom = targetScale;
            state.envelopeTo = targetScale;
            RapierBridge.syncMassProperties(subLevel, targetScale, false);
            ScaledBoundsCollider.applyScaledLocalBounds(subLevel);
            ScaleState.captureServerBounds(subLevel);
        }
    }

    public static void syncExternalStats(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        PivotDriftCompensation.before(subLevel);
        RapierBridge.syncMassProperties(subLevel, ScaleState.getServerScale(subLevel), true);
        PivotDriftCompensation.after(
                dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(subLevel.getLevel()).getPipeline(),
                subLevel);
        ScaleState.captureServerBounds(subLevel);
    }

    public static void forget(final UUID id) {
        if (id != null) STATES.remove(id);
    }

    private static State state(final ServerSubLevel subLevel) {
        return STATES.computeIfAbsent(subLevel.getUniqueId(), ignored -> new State());
    }

    private ScalePhysicsTransitions() {}
}
