package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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

    public void captureLimit(final ConstraintJointAxis axis, final double min, final double max) {
        if (this.replayingLimits) return;
        if (this.limits == null) this.limits = new Limit[AXES.length];
        this.limits[axis.ordinal()] = new Limit(min, max);
    }

    public void replayLimits(final GenericConstraintHandle handle) {
        if (this.limits == null || !handle.isValid()) return;
        this.replayingLimits = true;
        try {
            for (int i = 0; i < this.limits.length; i++) {
                final Limit limit = this.limits[i];
                if (limit != null) handle.setLimit(AXES[i], limit.min(), limit.max());
            }
        } finally {
            this.replayingLimits = false;
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
