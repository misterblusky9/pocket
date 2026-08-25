package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ColliderCoordinator {
    private static final int NATIVE_FLUID_VOID_OFFSET = 1 << 20;

    private static final double COLLIDER_SCALE_QUANTUM = 1.0D / 32.0D;

    private record StatsKey(long tick, int bodyId, int shapeRevision, long scaleBits) {}
    private record BoundsKey(int bodyId, CompiledCollider.Bounds bounds) {}
    private record RebuildGate(
            int bodyId,
            int shapeRevision,
            long pivotXBits,
            long pivotYBits,
            long pivotZBits,
            double scale
    ) {}

    private static final Map<UUID, StatsKey> LAST_STATS = new HashMap<>();
    private static final Map<UUID, CompiledCollider.GeometryKey> LAST_REQUESTS = new HashMap<>();
    private static final Map<UUID, ColliderCompiler.NativeImageKey> LAST_NATIVE_IMAGES = new HashMap<>();
    private static final Map<UUID, BoundsKey> LAST_BOUNDS = new HashMap<>();
    private static final Map<UUID, Long> GENERATIONS = new HashMap<>();
    private static final Map<UUID, Integer> COALESCED_REBUILDS = new HashMap<>();
    private static final Map<UUID, RebuildGate> LAST_REBUILD_GATES = new HashMap<>();

    public static synchronized boolean statsAlreadyServedThisTick(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;
        final UUID id = subLevel.getUniqueId();
        final StatsKey key = new StatsKey(
                subLevel.getLevel().getGameTime(),
                RapierBridge.bodyId(subLevel),
                PlotShapeCache.revision(id),
                Double.doubleToLongBits(ScaleState.getServerScale(subLevel)));
        return key.equals(LAST_STATS.put(id, key));
    }

    public static synchronized void rebuild(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        final UUID id = subLevel.getUniqueId();
        if (SubLevelLoadGuard.isLoading()) return;

        final int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) {
            PocketTrace.warn("scaled collider rebuild skipped: no live body {}", PocketTrace.context(subLevel));
            return;
        }
        if (PocketTrace.isUnsafeMutationPoint(subLevel)) {
            PocketTrace.warn("scaled collider rebuild at unsafe mutation point {}", PocketTrace.context(subLevel));
        }

        final int observedRevision = PlotShapeCache.revision(id);
        if (canDeferScaleOnlyRebuild(subLevel, id, bodyId, observedRevision)) {
            return;
        }

        final PlotShape shape = PlotShapeCache.get(subLevel);
        final int revision = PlotShapeCache.revision(id);
        final CompiledCollider.GeometryKey request = ColliderCompiler.requestKey(
                subLevel, bodyId, revision, shape != null);
        if (request == null) return;

        if (request.equals(LAST_REQUESTS.get(id)) && SableColliderMirror.has(id)) {
            rememberAppliedScale(subLevel, id, bodyId, revision);
            return;
        }

        final ColliderCompiler.NativeImageKey nativeImage = ColliderCompiler.nativeImageKey(
                subLevel, bodyId, shape);
        final ColliderCompiler.NativeImageKey previousNativeImage = LAST_NATIVE_IMAGES.get(id);
        final CompiledCollider previous = SableColliderMirror.current(id);
        if (nativeImage != null
                && nativeImage.equals(previousNativeImage)
                && previous != null
                && previous.bodyId() == bodyId) {
            final CompiledCollider retagged = ColliderCompiler.retag(previous, subLevel, request);
            if (retagged != null && SableColliderMirror.retagMetadata(retagged)) {
                PlotShapeCache.consumePending(id);
                LAST_REQUESTS.put(id, request);
                rememberAppliedScale(subLevel, id, bodyId, revision);
                traceCoalesced(id, subLevel, previous, retagged, revision);
                return;
            }
        }

        final PlotShapeCache.Region changed = PlotShapeCache.consumePending(id);
        final long generation = GENERATIONS.merge(id, 1L, Long::sum);
        final long start = System.nanoTime();
        final CompiledCollider compiled = ColliderCompiler.compile(
                subLevel, bodyId, generation, shape, revision, changed, previous);
        if (compiled == null) return;

        final boolean changedNative = SableColliderMirror.apply(subLevel, compiled);
        LAST_REQUESTS.put(id, request);
        if (nativeImage != null && compiled.mode() == request.mode()) {
            LAST_NATIVE_IMAGES.put(id, nativeImage);
        } else {
            LAST_NATIVE_IMAGES.remove(id);
        }
        COALESCED_REBUILDS.remove(id);
        rememberAppliedScale(subLevel, id, bodyId, revision);
        applyScaledLocalBounds(subLevel);

        final long elapsedMicros = (System.nanoTime() - start) / 1_000L;
        PocketTrace.scale(
                "collider compile uuid={} gen={} nativeChanged={} mode={} detail={} sections={} cells={} shapes={} approximateCells={} compileUs={}",
                id, compiled.generation(), changedNative, compiled.modeName(),
                shape == null ? "prism" : shape.level(), compiled.sections().size(),
                compiled.occupiedCellCount(), compiled.shapeCount(), compiled.approximateCellCount(), elapsedMicros);
    }

    public static synchronized CompiledCollider current(final UUID id) {
        return SableColliderMirror.current(id);
    }

    public static synchronized boolean hasSynthetic(final UUID id) {
        return SableColliderMirror.has(id);
    }

    public static synchronized void applyScaledLocalBounds(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null || SubLevelLoadGuard.isLoading()) return;
        final int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return;

        CompiledCollider.Bounds bounds = null;
        final CompiledCollider current = SableColliderMirror.current(subLevel.getUniqueId());
        if (current != null && current.bodyId() == bodyId) bounds = current.bounds();
        if (bounds == null) bounds = ColliderCompiler.boundsFor(subLevel);
        if (bounds == null) return;

        final BoundsKey key = new BoundsKey(bodyId, bounds);
        if (key.equals(LAST_BOUNDS.get(subLevel.getUniqueId()))) return;
        RapierBridge.setLocalBounds(
                subLevel.getLevel(), subLevel,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        LAST_BOUNDS.put(subLevel.getUniqueId(), key);
    }

    public static synchronized boolean suppressNativeFluidProbe(final ServerSubLevel subLevel) {
        if (subLevel == null || SubLevelLoadGuard.isLoading()) return false;
        final int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return false;
        final CompiledCollider.Bounds bounds = boundsOf(subLevel, bodyId);
        if (bounds == null) return false;

        final long above = (long) bounds.maxY() + NATIVE_FLUID_VOID_OFFSET;
        final long below = (long) bounds.minY() - NATIVE_FLUID_VOID_OFFSET;
        final int voidY;
        if (above <= Integer.MAX_VALUE) voidY = (int) above;
        else if (below >= Integer.MIN_VALUE) voidY = (int) below;
        else return false;

        RapierBridge.setLocalBoundsQuiet(
                subLevel.getLevel(), subLevel,
                bounds.minX(), voidY, bounds.minZ(),
                bounds.minX(), voidY, bounds.minZ());
        return true;
    }

    public static synchronized void restoreAfterNativeFluidProbe(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        final int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return;
        final CompiledCollider.Bounds bounds = boundsOf(subLevel, bodyId);
        if (bounds == null) return;

        RapierBridge.setLocalBoundsQuiet(
                subLevel.getLevel(), subLevel,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        LAST_BOUNDS.put(subLevel.getUniqueId(), new BoundsKey(bodyId, bounds));
    }

    public static synchronized void restoreOriginal(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        final UUID id = subLevel.getUniqueId();
        SableColliderMirror.restoreOriginal(subLevel);
        LAST_REQUESTS.remove(id);
        LAST_NATIVE_IMAGES.remove(id);
        LAST_BOUNDS.remove(id);
        LAST_STATS.remove(id);
        COALESCED_REBUILDS.remove(id);
        LAST_REBUILD_GATES.remove(id);
    }

    public static synchronized void forget(final UUID id) {
        if (id == null) return;
        SableColliderMirror.forget(id);
        LAST_REQUESTS.remove(id);
        LAST_NATIVE_IMAGES.remove(id);
        LAST_BOUNDS.remove(id);
        LAST_STATS.remove(id);
        GENERATIONS.remove(id);
        COALESCED_REBUILDS.remove(id);
        LAST_REBUILD_GATES.remove(id);
        PlotShapeCache.forget(id);
    }

    private static boolean canDeferScaleOnlyRebuild(
            final ServerSubLevel subLevel,
            final UUID id,
            final int bodyId,
            final int shapeRevision
    ) {
        final RebuildGate last = LAST_REBUILD_GATES.get(id);
        final CompiledCollider current = SableColliderMirror.current(id);
        if (last == null || current == null || current.bodyId() != bodyId) return false;
        if (last.bodyId() != bodyId || last.shapeRevision() != shapeRevision) return false;

        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (pivot == null) return false;
        if (last.pivotXBits() != Double.doubleToLongBits(pivot.x())
                || last.pivotYBits() != Double.doubleToLongBits(pivot.y())
                || last.pivotZBits() != Double.doubleToLongBits(pivot.z())) {
            return false;
        }

        final double scale = ScaleState.getServerScale(subLevel);
        if (!PocketSized.isValidScale(scale)) return false;

        final double delta = Math.abs(scale - last.scale());
        if (delta <= PocketSized.EPSILON) return true;

        if (isExactStageScale(scale)) return false;

        return delta < COLLIDER_SCALE_QUANTUM;
    }

    private static boolean isExactStageScale(final double scale) {
        for (final CompressionStage stage : CompressionStage.values()) {
            if (Math.abs(scale - stage.scale()) <= PocketSized.EPSILON) return true;
        }
        return false;
    }

    private static void rememberAppliedScale(
            final ServerSubLevel subLevel,
            final UUID id,
            final int bodyId,
            final int shapeRevision
    ) {
        final Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (pivot == null) {
            LAST_REBUILD_GATES.remove(id);
            return;
        }
        LAST_REBUILD_GATES.put(id, new RebuildGate(
                bodyId,
                shapeRevision,
                Double.doubleToLongBits(pivot.x()),
                Double.doubleToLongBits(pivot.y()),
                Double.doubleToLongBits(pivot.z()),
                ScaleState.getServerScale(subLevel)
        ));
    }

    private static void traceCoalesced(
            final UUID id,
            final ServerSubLevel subLevel,
            final CompiledCollider before,
            final CompiledCollider after,
            final int revision
    ) {
        if (!PocketTrace.SCALE || before == null || after == null) return;
        final int count = COALESCED_REBUILDS.merge(id, 1, Integer::sum);
        if (count != 1 && count % 100 != 0) return;

        PocketTrace.scale(
                "collider rebuild coalesced uuid={} count={} revision={} scale={} pivotDelta=[{},{},{}] sections={} cells={}",
                id, count, revision, ScaleState.getServerScale(subLevel),
                after.pivotX() - before.pivotX(),
                after.pivotY() - before.pivotY(),
                after.pivotZ() - before.pivotZ(),
                after.sections().size(), after.occupiedCellCount());
    }

    private static CompiledCollider.Bounds boundsOf(final ServerSubLevel subLevel, final int bodyId) {
        final CompiledCollider current = SableColliderMirror.current(subLevel.getUniqueId());
        if (current != null && current.bodyId() == bodyId) return current.bounds();
        return ColliderCompiler.boundsFor(subLevel);
    }

    private ColliderCoordinator() {}
}
