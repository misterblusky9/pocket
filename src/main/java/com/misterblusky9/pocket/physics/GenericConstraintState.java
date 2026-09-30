package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
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
        GenericConstraintState pocket$genericState();

        void pocket$trackGeneric(
                PhysicsPipelineBody body1, PhysicsPipelineBody body2, GenericConstraintState state);
    }

    private static final ConstraintJointAxis[] AXES = ConstraintJointAxis.values();

    private final Frame frame1;
    private final Frame frame2;
    private final GenericConstraintConfiguration configuration;
    private Limit[] limits;
    private boolean replayingLimits;
    private Set<ConstraintJointAxis> lockedAxes;
    private boolean replayingLockedAxes;

    public GenericConstraintState(
            final GenericConstraintConfiguration configuration,
            final Vector3dc pivot1, final double scale1,
            final Vector3dc pivot2, final double scale2
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
            final boolean first, final Vector3dc position, final Quaterniondc orientation,
            final Vector3dc pivot, final double scale
    ) {
        final Frame frame = first ? this.frame1 : this.frame2;
        frame.position.set(position);
        frame.orientation.set(orientation);
        frame.bake(pivot, scale);
    }

    public void bake(
            final Vector3dc pivot1, final double scale1,
            final Vector3dc pivot2, final double scale2
    ) {
        this.frame1.bake(pivot1, scale1);
        this.frame2.bake(pivot2, scale2);
    }

    public boolean anchorsWouldMove(
            final Vector3dc pivot1, final double scale1,
            final Vector3dc pivot2, final double scale2
    ) {
        return this.frame1.wouldMove(pivot1, scale1) || this.frame2.wouldMove(pivot2, scale2);
    }

    public static boolean isLinear(final ConstraintJointAxis axis) {
        return axis == ConstraintJointAxis.LINEAR_X
                || axis == ConstraintJointAxis.LINEAR_Y
                || axis == ConstraintJointAxis.LINEAR_Z;
    }

    // A linear limit is relative joint travel, not a coordinate owned by either end. It only has a
    // nominal reading when both ends share a scale; a mismatched pair has no single nominal frame,
    // so it stays in metric units rather than inheriting an endpoint's scale.
    public static double limitScale(
            final ConstraintJointAxis axis, final double scale1, final double scale2
    ) {
        if (!isLinear(axis)) return 1.0D;
        final double s1 = sane(scale1);
        final double s2 = sane(scale2);
        return Math.abs(s1 - s2) > PocketSized.EPSILON ? 1.0D : s1;
    }

    public static double toMetricLimit(
            final ConstraintJointAxis axis, final double value, final double scale1, final double scale2
    ) {
        final double scale = limitScale(axis, scale1, scale2);
        return scale == 1.0D ? value : value * scale;
    }

    private static double sane(final double scale) {
        return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }

    public boolean isReplayingLimits() {
        return this.replayingLimits;
    }

    public void captureLimit(final ConstraintJointAxis axis, final double min, final double max) {
        if (this.replayingLimits) return;
        if (this.limits == null) this.limits = new Limit[AXES.length];
        this.limits[axis.ordinal()] = new Limit(min, max);
    }

    public void replayLimits(
            final GenericConstraintHandle handle, final double scale1, final double scale2
    ) {
        if (this.limits == null || !handle.isValid()) return;
        this.replayingLimits = true;
        try {
            for (int i = 0; i < this.limits.length; i++) {
                final Limit limit = this.limits[i];
                if (limit == null) continue;
                final ConstraintJointAxis axis = AXES[i];
                handle.setLimit(axis,
                        toMetricLimit(axis, limit.min(), scale1, scale2),
                        toMetricLimit(axis, limit.max(), scale1, scale2));
            }
        } finally {
            this.replayingLimits = false;
        }
    }

    // lockAxes replaces the whole lock mask, so the newest call is the whole truth.
    public void captureLockedAxes(final ConstraintJointAxis... axes) {
        if (this.replayingLockedAxes) return;
        final EnumSet<ConstraintJointAxis> locked = EnumSet.noneOf(ConstraintJointAxis.class);
        if (axes != null) {
            for (final ConstraintJointAxis axis : axes) {
                if (axis != null) locked.add(axis);
            }
        }
        this.lockedAxes = locked;
    }

    public void replayLockedAxes(final GenericConstraintHandle handle) {
        if (this.lockedAxes == null || !handle.isValid()) return;
        this.replayingLockedAxes = true;
        try {
            handle.lockAxes(this.lockedAxes.toArray(new ConstraintJointAxis[0]));
        } finally {
            this.replayingLockedAxes = false;
        }
    }

    private record Limit(double min, double max) {}

    private static final class Frame {
        private final Vector3d position;
        private final Quaterniond orientation;
        private Vector3dc pivot;
        private double scale;

        private Frame(final Vector3dc position, final Quaterniondc orientation) {
            this.position = new Vector3d(position);
            this.orientation = new Quaterniond(orientation);
        }

        private void bake(final Vector3dc pivot, final double scale) {
            this.pivot = pivot == null ? null : new Vector3d(pivot);
            this.scale = scale;
        }

        private boolean wouldMove(final Vector3dc pivot, final double scale) {
            if (this.pivot == null || pivot == null) return false;
            final double dx = (this.position.x - pivot.x()) * scale
                    - (this.position.x - this.pivot.x()) * this.scale;
            final double dy = (this.position.y - pivot.y()) * scale
                    - (this.position.y - this.pivot.y()) * this.scale;
            final double dz = (this.position.z - pivot.z()) * scale
                    - (this.position.z - this.pivot.z()) * this.scale;
            return dx * dx + dy * dy + dz * dz > 1.0E-12D;
        }
    }
}
