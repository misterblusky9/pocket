package com.misterblusky9.pocket.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.physics.GenericConstraintState;
import com.misterblusky9.pocket.physics.ScaleFrame;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle", remap = false)
public abstract class GenericConstraintHandleScaleMixin implements GenericConstraintState.Access {
    @Unique
    private PhysicsPipelineBody pocket$body1;

    @Unique
    private PhysicsPipelineBody pocket$body2;

    @Unique
    private GenericConstraintState pocket$state;

    @Override
    public GenericConstraintState pocket$genericState() {
        return this.pocket$state;
    }

    @Override
    public void pocket$trackGeneric(
            final PhysicsPipelineBody body1, final PhysicsPipelineBody body2,
            final GenericConstraintState state
    ) {
        this.pocket$body1 = body1;
        this.pocket$body2 = body2;
        this.pocket$state = state;
    }

    @WrapMethod(method = "setFrame1", remap = false)
    private void pocket$setFrame1(
            final Vector3dc position, final Quaterniondc orientation, final Operation<Void> original
    ) {
        original.call(ScaleFrame.toBodyMetric(this.pocket$body1, position), orientation);
        this.pocket$captureFrame(true, this.pocket$body1, position, orientation);
    }

    @WrapMethod(method = "setFrame2", remap = false)
    private void pocket$setFrame2(
            final Vector3dc position, final Quaterniondc orientation, final Operation<Void> original
    ) {
        original.call(ScaleFrame.toBodyMetric(this.pocket$body2, position), orientation);
        this.pocket$captureFrame(false, this.pocket$body2, position, orientation);
    }

    @Unique
    private void pocket$captureFrame(
            final boolean first, final PhysicsPipelineBody body,
            final Vector3dc position, final Quaterniondc orientation
    ) {
        if (this.pocket$state == null) return;
        this.pocket$state.captureFrame(first, position, orientation,
                body instanceof final ServerSubLevel subLevel ? ScaleFrame.pivot(subLevel) : null,
                ScaleFrame.scaleOf(body));
    }

    @WrapMethod(method = "setLimit", remap = false)
    private void pocket$setLimit(
            final ConstraintJointAxis axis, final double min, final double max, final Operation<Void> original
    ) {
        final GenericConstraintState state = this.pocket$state;

        // A replay already carries metric values; scaling them again would compound.
        if (state != null && state.isReplayingLimits()) {
            original.call(axis, min, max);
            return;
        }

        final double scale1 = ScaleFrame.scaleOf(this.pocket$body1);
        final double scale2 = ScaleFrame.scaleOf(this.pocket$body2);
        original.call(axis,
                GenericConstraintState.toMetricLimit(axis, min, scale1, scale2),
                GenericConstraintState.toMetricLimit(axis, max, scale1, scale2));

        if (state != null) state.captureLimit(axis, min, max);
    }

    @WrapMethod(method = "lockAxes", remap = false)
    private void pocket$lockAxes(final ConstraintJointAxis[] axes, final Operation<Void> original) {
        original.call((Object) axes);
        if (this.pocket$state != null) this.pocket$state.captureLockedAxes(axes);
    }
}
