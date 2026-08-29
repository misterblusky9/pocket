package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ScaleFrame {
    public static double scaleOf(final PhysicsPipelineBody body) {
        if (!(body instanceof final ServerSubLevel subLevel)) return 1.0D;
        final double scale = ScaleState.getServerScale(subLevel);
        if (!Double.isFinite(scale) || scale <= 0.0D) return 1.0D;
        return scale;
    }

    public static boolean isScaled(final PhysicsPipelineBody body) {
        return Math.abs(scaleOf(body) - 1.0D) > PocketSized.EPSILON;
    }

    public static Vector3dc pivot(final ServerSubLevel subLevel) {
        return subLevel.logicalPose().rotationPoint();
    }

    public static Vector3dc toBodyMetric(final PhysicsPipelineBody body, final Vector3dc plotPoint) {
        if (plotPoint == null || !(body instanceof final ServerSubLevel subLevel)) return plotPoint;

        final double scale = scaleOf(body);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return plotPoint;

        final Vector3dc pivot = pivot(subLevel);
        if (pivot == null) return plotPoint;

        return contract(pivot, plotPoint, scale, new Vector3d());
    }

    public static Vector3dc toBodyMetricAt(
            final PhysicsPipelineBody body, final Vector3dc plotPoint, final double scale
    ) {
        if (plotPoint == null || !(body instanceof final ServerSubLevel subLevel)) return plotPoint;
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return plotPoint;

        final Vector3dc pivot = pivot(subLevel);
        if (pivot == null) return plotPoint;

        return contract(pivot, plotPoint, scale, new Vector3d());
    }

    public static Vector3d contract(
            final Vector3dc pivot,
            final Vector3dc point,
            final double scale,
            final Vector3d dest
    ) {
        return dest.set(
                pivot.x() + (point.x() - pivot.x()) * scale,
                pivot.y() + (point.y() - pivot.y()) * scale,
                pivot.z() + (point.z() - pivot.z()) * scale
        );
    }

    public static double contract(final double pivot, final double value, final double scale) {
        return pivot + (value - pivot) * scale;
    }

    private ScaleFrame() {}
}
