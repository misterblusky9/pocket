package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PivotDriftCompensation {
    private record Before(Vector3d pivot, Vector3d position) {}

    private static final Map<UUID, Before> PENDING = new ConcurrentHashMap<>();

    private static final double MIN_PIVOT_SHIFT = 1.0E-6D;

    public static void before(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return;

        UUID id = subLevel.getUniqueId();
        if (id == null) return;

        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (pivot == null) {
            PENDING.remove(id);
            return;
        }

        PENDING.put(id, new Before(
                new Vector3d(pivot), new Vector3d(subLevel.logicalPose().position())));
    }

    public static void after(PhysicsPipeline pipeline, ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return;

        UUID id = subLevel.getUniqueId();
        if (id == null) return;

        Before before = PENDING.remove(id);
        if (before == null || pipeline == null) return;

        double scale = ScaleState.getServerScale(subLevel);
        if (!ScaleBounds.isValid(scale)) return;

        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (pivot == null) return;

        Vector3d shift = new Vector3d(pivot).sub(before.pivot());
        if (shift.lengthSquared() <= MIN_PIVOT_SHIFT * MIN_PIVOT_SHIFT) return;

        Quaterniond orientation = new Quaterniond(subLevel.logicalPose().orientation());
        Vector3d correction = new Vector3d(shift).mul(scale);
        orientation.transform(correction);

        Vector3d corrected = new Vector3d(before.position()).add(correction);

        Vector3d current = subLevel.logicalPose().position();
        if (corrected.distanceSquared(current) <= MIN_PIVOT_SHIFT * MIN_PIVOT_SHIFT) return;

        PymTrace.scale(
                "centre of mass moved by {} at scale {}; holding the craft still (drift would have been {})",
                shift.length(), scale, corrected.distance(current));

        pipeline.teleport(subLevel, corrected, subLevel.logicalPose().orientation());
        subLevel.logicalPose().position().set(corrected);
        subLevel.updateBoundingBox();
    }

    public static void forget(UUID id) {
        if (id != null) PENDING.remove(id);
    }

    private PivotDriftCompensation() {}
}
