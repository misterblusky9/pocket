package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.physics.GenericConstraintState;
import com.misterblusky9.pym.internal.physics.ScaleFrame;
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
    private PhysicsPipelineBody pym$body1;

    @Unique
    private PhysicsPipelineBody pym$body2;

    @Unique
    private GenericConstraintState pym$state;

    @Override
    public GenericConstraintState pym$genericState() {
        return this.pym$state;
    }

    @Override
    public void pym$trackGeneric(
            final PhysicsPipelineBody body1, final PhysicsPipelineBody body2,
            final GenericConstraintState state
    ) {
        this.pym$body1 = body1;
        this.pym$body2 = body2;
        this.pym$state = state;
    }

    @WrapMethod(method = "setFrame1", remap = false)
    private void pym$setFrame1(
            final Vector3dc position, final Quaterniondc orientation, final Operation<Void> original
    ) {
        original.call(ScaleFrame.toBodyMetric(this.pym$body1, position), orientation);
        this.pym$captureFrame(true, this.pym$body1, position, orientation);
    }

    @WrapMethod(method = "setFrame2", remap = false)
    private void pym$setFrame2(
            final Vector3dc position, final Quaterniondc orientation, final Operation<Void> original
    ) {
        original.call(ScaleFrame.toBodyMetric(this.pym$body2, position), orientation);
        this.pym$captureFrame(false, this.pym$body2, position, orientation);
    }

    @Unique
    private void pym$captureFrame(
            final boolean first, final PhysicsPipelineBody body,
            final Vector3dc position, final Quaterniondc orientation
    ) {
        if (this.pym$state == null) return;
        this.pym$state.captureFrame(first, position, orientation,
                body instanceof final ServerSubLevel subLevel ? ScaleFrame.pivot(subLevel) : null,
                ScaleFrame.scaleOf(body));
    }

    @WrapMethod(method = "setLimit", remap = false)
    private void pym$setLimit(
            final ConstraintJointAxis axis, final double min, final double max, final Operation<Void> original
    ) {
        final GenericConstraintState state = this.pym$state;

        if (state != null && state.isReplayingLimits()) {
            original.call(axis, min, max);
            return;
        }

        final double scale1 = ScaleFrame.scaleOf(this.pym$body1);
        final double scale2 = ScaleFrame.scaleOf(this.pym$body2);
        original.call(axis,
                GenericConstraintState.toMetricLimit(axis, min, scale1, scale2),
                GenericConstraintState.toMetricLimit(axis, max, scale1, scale2));

        if (state != null) state.captureLimit(axis, min, max);
    }

    @WrapMethod(method = "lockAxes", remap = false)
    private void pym$lockAxes(final ConstraintJointAxis[] axes, final Operation<Void> original) {
        original.call((Object) axes);
        if (this.pym$state != null) this.pym$state.captureLockedAxes(axes);
    }
}
