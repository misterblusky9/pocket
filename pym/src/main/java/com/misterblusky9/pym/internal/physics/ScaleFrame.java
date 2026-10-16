package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ScaleFrame {
    public static double scaleOf(PhysicsPipelineBody body) {
        if (!(body instanceof ServerSubLevel subLevel)) return 1.0D;
        double scale = ScaleState.getServerScale(subLevel);
        if (!Double.isFinite(scale) || scale <= 0.0D) return 1.0D;
        return scale;
    }

    public static boolean isScaled(PhysicsPipelineBody body) {
        return Math.abs(scaleOf(body) - 1.0D) > ScaleBounds.EPSILON;
    }

    public static Vector3dc pivot(ServerSubLevel subLevel) {
        return subLevel.logicalPose().rotationPoint();
    }

    public static Vector3dc toBodyMetric(PhysicsPipelineBody body, Vector3dc plotPoint) {
        if (plotPoint == null || !(body instanceof ServerSubLevel subLevel)) return plotPoint;

        double scale = scaleOf(body);
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return plotPoint;

        Vector3dc pivot = pivot(subLevel);
        if (pivot == null) return plotPoint;

        return contract(pivot, plotPoint, scale, new Vector3d());
    }

    public static Vector3dc toBodyMetricAt(
            PhysicsPipelineBody body, Vector3dc plotPoint, double scale
    ) {
        if (plotPoint == null || !(body instanceof ServerSubLevel subLevel)) return plotPoint;
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return plotPoint;

        Vector3dc pivot = pivot(subLevel);
        if (pivot == null) return plotPoint;

        return contract(pivot, plotPoint, scale, new Vector3d());
    }

    public static Vector3d contract(
            Vector3dc pivot,
            Vector3dc point,
            double scale,
            Vector3d dest
    ) {
        return dest.set(
                pivot.x() + (point.x() - pivot.x()) * scale,
                pivot.y() + (point.y() - pivot.y()) * scale,
                pivot.z() + (point.z() - pivot.z()) * scale
        );
    }

    public static double contract(double pivot, double value, double scale) {
        return pivot + (value - pivot) * scale;
    }

    private ScaleFrame() {}
}
