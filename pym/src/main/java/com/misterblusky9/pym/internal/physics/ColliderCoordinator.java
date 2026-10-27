package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ColliderCoordinator {
    private static final int NATIVE_FLUID_VOID_OFFSET = 1 << 20;

    private record StatsKey(long tick, int bodyId, int shapeRevision, long scaleBits) {}
    private record BoundsKey(int bodyId, CompiledCollider.Bounds bounds) {}
    private static final Map<UUID, StatsKey> LAST_STATS = new HashMap<>();
    private static final Map<UUID, CompiledCollider.GeometryKey> LAST_REQUESTS = new HashMap<>();
    private static final Map<UUID, ColliderCompiler.NativeImageKey> LAST_NATIVE_IMAGES = new HashMap<>();
    private static final Map<UUID, BoundsKey> LAST_BOUNDS = new HashMap<>();
    private static final Map<UUID, Long> GENERATIONS = new HashMap<>();
    private static final Map<UUID, Integer> COALESCED_REBUILDS = new HashMap<>();

    public static synchronized boolean statsAlreadyServedThisTick(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;
        UUID id = subLevel.getUniqueId();
        StatsKey key = new StatsKey(
                subLevel.getLevel().getGameTime(),
                RapierBridge.bodyId(subLevel),
                PlotShapeCache.revision(id),
                Double.doubleToLongBits(ScaleState.getServerScale(subLevel)));
        return key.equals(LAST_STATS.put(id, key));
    }

    public static synchronized void rebuild(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        UUID id = subLevel.getUniqueId();
        if (SubLevelLoadGuard.isLoading()) return;

        int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) {
            PymTrace.warn("scaled collider rebuild skipped: no live body {}", PymTrace.context(subLevel));
            return;
        }
        if (PymTrace.isUnsafeMutationPoint(subLevel)) {
            PymTrace.warn("scaled collider rebuild at unsafe mutation point {}", PymTrace.context(subLevel));
        }

        PlotShape shape = ScalePhysicsTransitions.usePrism(subLevel)
                ? null
                : PlotShapeCache.get(subLevel);
        int revision = PlotShapeCache.revision(id);
        CompiledCollider.GeometryKey request = ColliderCompiler.requestKey(
                subLevel, bodyId, revision, shape != null);
        if (request == null) return;

        if (request.equals(LAST_REQUESTS.get(id)) && SableColliderMirror.has(id)) {
            return;
        }

        ColliderCompiler.NativeImageKey nativeImage = ColliderCompiler.nativeImageKey(
                subLevel, bodyId, shape);
        ColliderCompiler.NativeImageKey previousNativeImage = LAST_NATIVE_IMAGES.get(id);
        CompiledCollider previous = SableColliderMirror.current(id);
        if (nativeImage != null
                && nativeImage.equals(previousNativeImage)
                && previous != null
                && previous.bodyId() == bodyId) {
            CompiledCollider retagged = ColliderCompiler.retag(previous, subLevel, request);
            if (retagged != null && SableColliderMirror.retagMetadata(retagged)) {
                PlotShapeCache.consumePending(id);
                LAST_REQUESTS.put(id, request);
                traceCoalesced(id, subLevel, previous, retagged, revision);
                return;
            }
        }

        PlotShapeCache.Region changed = PlotShapeCache.consumePending(id);
        long generation = GENERATIONS.merge(id, 1L, Long::sum);
        long start = System.nanoTime();
        CompiledCollider compiled = ColliderCompiler.compile(
                subLevel, bodyId, generation, shape, revision, changed, previous);
        if (compiled == null) return;

        CompiledCollider applied = compiled;
        boolean changedNative;
        applyScaledLocalBounds(subLevel);
        try {
            changedNative = SableColliderMirror.apply(subLevel, applied);
        } catch (ShapeRegistry.CapacityExceededException capacity) {
            if (applied.mode() == CompiledCollider.Mode.PRISM_FALLBACK) throw capacity;
            PymTrace.warn(
                    "scaled collider exceeded Sable packed shape capacity; using prism fallback uuid={} gen={} sections={} cells={} shapes={} error={}",
                    id, applied.generation(), applied.sections().size(), applied.occupiedCellCount(),
                    applied.shapeCount(), capacity.getMessage());
            applied = ColliderCompiler.compile(
                    subLevel, bodyId, generation, null, revision, PlotShapeCache.Region.FULL,
                    SableColliderMirror.current(id));
            if (applied == null) throw capacity;
            changedNative = SableColliderMirror.apply(subLevel, applied);
        }
        LAST_REQUESTS.put(id, request);
        if (nativeImage != null && applied.mode() == request.mode()) {
            LAST_NATIVE_IMAGES.put(id, nativeImage);
        } else {
            LAST_NATIVE_IMAGES.remove(id);
        }
        COALESCED_REBUILDS.remove(id);
        applyScaledLocalBounds(subLevel);

        long elapsedMicros = (System.nanoTime() - start) / 1_000L;
        PymTrace.scale(
                "collider compile uuid={} gen={} nativeChanged={} mode={} detail={} sections={} cells={} shapes={} approximateCells={} compileUs={}",
                id, applied.generation(), changedNative, applied.modeName(),
                applied.mode() == CompiledCollider.Mode.PRISM_FALLBACK ? "prism" : shape.level(), applied.sections().size(),
                applied.occupiedCellCount(), applied.shapeCount(), applied.approximateCellCount(), elapsedMicros);
    }

    public static synchronized CompiledCollider current(UUID id) {
        return SableColliderMirror.current(id);
    }

    public static synchronized boolean hasSynthetic(UUID id) {
        return SableColliderMirror.has(id);
    }

    public static synchronized void applyScaledLocalBounds(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null || SubLevelLoadGuard.isLoading()) return;
        int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return;

        CompiledCollider.Bounds bounds = ColliderCompiler.boundsFor(subLevel);
        if (bounds == null) return;
        bounds = ScalePhysicsTransitions.nativeBounds(subLevel, bounds);

        BoundsKey key = new BoundsKey(bodyId, bounds);
        if (key.equals(LAST_BOUNDS.get(subLevel.getUniqueId()))) return;
        RapierBridge.setLocalBounds(
                subLevel.getLevel(), subLevel,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        LAST_BOUNDS.put(subLevel.getUniqueId(), key);
    }

    public static synchronized CompiledCollider.Bounds nativeLocalBounds(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return null;
        int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return null;
        BoundsKey applied = LAST_BOUNDS.get(subLevel.getUniqueId());
        if (applied != null && applied.bodyId() == bodyId) return applied.bounds();
        CompiledCollider.Bounds bounds = boundsOf(subLevel, bodyId);
        return bounds == null ? null : ScalePhysicsTransitions.nativeBounds(subLevel, bounds);
    }

    public static synchronized boolean suppressNativeFluidProbe(ServerSubLevel subLevel) {
        if (subLevel == null || SubLevelLoadGuard.isLoading()) return false;
        int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return false;
        CompiledCollider.Bounds bounds = boundsOf(subLevel, bodyId);
        if (bounds == null) return false;

        long above = (long) bounds.maxY() + NATIVE_FLUID_VOID_OFFSET;
        long below = (long) bounds.minY() - NATIVE_FLUID_VOID_OFFSET;
        int voidY;
        if (above <= Integer.MAX_VALUE) voidY = (int) above;
        else if (below >= Integer.MIN_VALUE) voidY = (int) below;
        else return false;

        RapierBridge.setLocalBoundsQuiet(
                subLevel.getLevel(), subLevel,
                bounds.minX(), voidY, bounds.minZ(),
                bounds.minX(), voidY, bounds.minZ());
        return true;
    }

    public static synchronized void restoreAfterNativeFluidProbe(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        int bodyId = RapierBridge.bodyId(subLevel);
        if (bodyId == RapierBridge.NO_BODY) return;
        CompiledCollider.Bounds bounds = boundsOf(subLevel, bodyId);
        if (bounds == null) return;
        bounds = ScalePhysicsTransitions.nativeBounds(subLevel, bounds);

        RapierBridge.setLocalBoundsQuiet(
                subLevel.getLevel(), subLevel,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        LAST_BOUNDS.put(subLevel.getUniqueId(), new BoundsKey(bodyId, bounds));
    }

    public static synchronized void restoreOriginal(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        UUID id = subLevel.getUniqueId();
        SableColliderMirror.restoreOriginal(subLevel);
        LAST_REQUESTS.remove(id);
        LAST_NATIVE_IMAGES.remove(id);
        LAST_BOUNDS.remove(id);
        LAST_STATS.remove(id);
        COALESCED_REBUILDS.remove(id);
    }

    public static synchronized void forget(UUID id) {
        if (id == null) return;
        SableColliderMirror.forget(id);
        LAST_REQUESTS.remove(id);
        LAST_NATIVE_IMAGES.remove(id);
        LAST_BOUNDS.remove(id);
        LAST_STATS.remove(id);
        GENERATIONS.remove(id);
        COALESCED_REBUILDS.remove(id);
        PlotShapeCache.forget(id);
    }

    private static void traceCoalesced(
            UUID id,
            ServerSubLevel subLevel,
            CompiledCollider before,
            CompiledCollider after,
            int revision
    ) {
        if (!PymTrace.enabled() || before == null || after == null) return;
        int count = COALESCED_REBUILDS.merge(id, 1, Integer::sum);
        if (count != 1 && count % 100 != 0) return;

        PymTrace.scale(
                "collider rebuild coalesced uuid={} count={} revision={} scale={} pivotDelta=[{},{},{}] sections={} cells={}",
                id, count, revision, ScaleState.getServerScale(subLevel),
                after.pivotX() - before.pivotX(),
                after.pivotY() - before.pivotY(),
                after.pivotZ() - before.pivotZ(),
                after.sections().size(), after.occupiedCellCount());
    }

    private static CompiledCollider.Bounds boundsOf(ServerSubLevel subLevel, int bodyId) {
        CompiledCollider current = SableColliderMirror.current(subLevel.getUniqueId());
        if (current != null && current.bodyId() == bodyId) return current.bounds();
        return ColliderCompiler.boundsFor(subLevel);
    }

    private ColliderCoordinator() {}
}
