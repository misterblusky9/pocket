package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.EnumSet;
import java.util.Set;

public final class GenericConstraintState {
    public interface Access {
        GenericConstraintState pym$genericState();

        void pym$trackGeneric(
                PhysicsPipelineBody body1, PhysicsPipelineBody body2, GenericConstraintState state);
    }

    private static final ConstraintJointAxis[] AXES = ConstraintJointAxis.values();

    private final Frame frame1;
    private final Frame frame2;
    private final GenericConstraintConfiguration configuration;
    private Limit[] limits;
    private boolean replayingLimits;
    private boolean metricLinearLimits;
    private Set<ConstraintJointAxis> lockedAxes;
    private boolean replayingLockedAxes;

    public GenericConstraintState(
            GenericConstraintConfiguration configuration,
            Vector3dc pivot1, double scale1,
            Vector3dc pivot2, double scale2
    ) {
        this.frame1 = new Frame(configuration.pos1(), configuration.orientation1());
        this.frame2 = new Frame(configuration.pos2(), configuration.orientation2());
        this.configuration = new GenericConstraintConfiguration(
                this.frame1.position, this.frame2.position,
                this.frame1.orientation, this.frame2.orientation,
                Set.copyOf(configuration.lockedAxes()));
        this.bake(pivot1, scale1, pivot2, scale2);
    }

    public GenericConstraintConfiguration configuration() {
        return this.configuration;
    }

    public void captureFrame(
            boolean first, Vector3dc position, Quaterniondc orientation,
            Vector3dc pivot, double scale
    ) {
        Frame frame = first ? this.frame1 : this.frame2;
        frame.position.set(position);
        frame.orientation.set(orientation);
        frame.bake(pivot, scale);
    }

    public void bake(
            Vector3dc pivot1, double scale1,
            Vector3dc pivot2, double scale2
    ) {
        this.frame1.bake(pivot1, scale1);
        this.frame2.bake(pivot2, scale2);
    }

    public boolean anchorsWouldMove(
            Vector3dc pivot1, double scale1,
            Vector3dc pivot2, double scale2
    ) {
        return this.frame1.wouldMove(pivot1, scale1) || this.frame2.wouldMove(pivot2, scale2);
    }

    public static boolean isLinear(ConstraintJointAxis axis) {
        return axis == ConstraintJointAxis.LINEAR_X
                || axis == ConstraintJointAxis.LINEAR_Y
                || axis == ConstraintJointAxis.LINEAR_Z;
    }

    public static double limitScale(
            ConstraintJointAxis axis, double scale1, double scale2
    ) {
        if (!isLinear(axis)) return 1.0D;
        double s1 = sane(scale1);
        double s2 = sane(scale2);
        return Math.abs(s1 - s2) > ScaleBounds.EPSILON ? 1.0D : s1;
    }

    public static double toMetricLimit(
            ConstraintJointAxis axis, double value, double scale1, double scale2
    ) {
        double scale = limitScale(axis, scale1, scale2);
        return scale == 1.0D ? value : value * scale;
    }

    private static double sane(double scale) {
        return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }

    public boolean isReplayingLimits() {
        return this.replayingLimits;
    }

    public void useMetricLinearLimits() {
        this.metricLinearLimits = true;
    }

    public boolean hasMetricLinearLimits() {
        return this.metricLinearLimits;
    }

    public void captureLimit(ConstraintJointAxis axis, double min, double max) {
        if (this.replayingLimits) return;
        if (this.limits == null) this.limits = new Limit[AXES.length];
        this.limits[axis.ordinal()] = new Limit(min, max, false);
    }

    public void captureMetricLimit(ConstraintJointAxis axis, double min, double max) {
        if (this.replayingLimits) return;
        if (this.limits == null) this.limits = new Limit[AXES.length];
        this.limits[axis.ordinal()] = new Limit(min, max, true);
    }

    public void replayLimits(
            GenericConstraintHandle handle, double scale1, double scale2
    ) {
        if (this.limits == null || !handle.isValid()) return;
        this.replayingLimits = true;
        try {
            for (int i = 0; i < this.limits.length; i++) {
                Limit limit = this.limits[i];
                if (limit == null) continue;
                ConstraintJointAxis axis = AXES[i];
                boolean metric = this.metricLinearLimits || limit.worldMetric();
                handle.setLimit(axis,
                        metric ? limit.min() : toMetricLimit(axis, limit.min(), scale1, scale2),
                        metric ? limit.max() : toMetricLimit(axis, limit.max(), scale1, scale2));
            }
        } finally {
            this.replayingLimits = false;
        }
    }

    public void captureLockedAxes(ConstraintJointAxis... axes) {
        if (this.replayingLockedAxes) return;
        EnumSet<ConstraintJointAxis> locked = EnumSet.noneOf(ConstraintJointAxis.class);
        if (axes != null) {
            for (ConstraintJointAxis axis : axes) {
                if (axis != null) locked.add(axis);
            }
        }
        this.lockedAxes = locked;
    }

    public void replayLockedAxes(GenericConstraintHandle handle) {
        if (this.lockedAxes == null || !handle.isValid()) return;
        this.replayingLockedAxes = true;
        try {
            handle.lockAxes(this.lockedAxes.toArray(new ConstraintJointAxis[0]));
        } finally {
            this.replayingLockedAxes = false;
        }
    }

    private record Limit(double min, double max, boolean worldMetric) {}

    private static final class Frame {
        private final Vector3d position;
        private final Quaterniond orientation;
        private Vector3dc pivot;
        private double scale;

        private Frame(Vector3dc position, Quaterniondc orientation) {
            this.position = new Vector3d(position);
            this.orientation = new Quaterniond(orientation);
        }

        private void bake(Vector3dc pivot, double scale) {
            this.pivot = pivot == null ? null : new Vector3d(pivot);
            this.scale = scale;
        }

        private boolean wouldMove(Vector3dc pivot, double scale) {
            if (this.pivot == null || pivot == null) return false;
            double dx = (this.position.x - pivot.x()) * scale
                    - (this.position.x - this.pivot.x()) * this.scale;
            double dy = (this.position.y - pivot.y()) * scale
                    - (this.position.y - this.pivot.y()) * this.scale;
            double dz = (this.position.z - pivot.z()) * scale
                    - (this.position.z - this.pivot.z()) * this.scale;
            return dx * dx + dy * dy + dz * dz > 1.0E-12D;
        }
    }
}
