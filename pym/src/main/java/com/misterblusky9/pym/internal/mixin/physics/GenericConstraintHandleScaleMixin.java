package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.compat.simurail.SimurailJointLimits;
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
            PhysicsPipelineBody body1, PhysicsPipelineBody body2,
            GenericConstraintState state
    ) {
        this.pym$body1 = body1;
        this.pym$body2 = body2;
        this.pym$state = state;
    }

    @WrapMethod(method = "setFrame1", remap = false)
    private void pym$setFrame1(
            Vector3dc position, Quaterniondc orientation, Operation<Void> original
    ) {
        original.call(ScaleFrame.toBodyMetric(this.pym$body1, position), orientation);
        this.pym$captureFrame(true, this.pym$body1, position, orientation);
    }

    @WrapMethod(method = "setFrame2", remap = false)
    private void pym$setFrame2(
            Vector3dc position, Quaterniondc orientation, Operation<Void> original
    ) {
        original.call(ScaleFrame.toBodyMetric(this.pym$body2, position), orientation);
        this.pym$captureFrame(false, this.pym$body2, position, orientation);
    }

    @Unique
    private void pym$captureFrame(
            boolean first, PhysicsPipelineBody body,
            Vector3dc position, Quaterniondc orientation
    ) {
        if (this.pym$state == null) return;
        this.pym$state.captureFrame(first, position, orientation,
                body instanceof ServerSubLevel subLevel ? ScaleFrame.pivot(subLevel) : null,
                ScaleFrame.scaleOf(body));
    }

    @WrapMethod(method = "setLimit", remap = false)
    private void pym$setLimit(
            ConstraintJointAxis axis, double min, double max, Operation<Void> original
    ) {
        GenericConstraintState state = this.pym$state;

        if (state != null && state.isReplayingLimits()) {
            original.call(axis, min, max);
            return;
        }

        if (SimurailJointLimits.worldMetricContext()) {
            original.call(axis, min, max);
            if (state != null) state.captureMetricLimit(axis, min, max);
            return;
        }

        double scale1 = ScaleFrame.scaleOf(this.pym$body1);
        double scale2 = ScaleFrame.scaleOf(this.pym$body2);
        boolean metric = state != null && state.hasMetricLinearLimits();
        original.call(axis,
                metric ? min : GenericConstraintState.toMetricLimit(axis, min, scale1, scale2),
                metric ? max : GenericConstraintState.toMetricLimit(axis, max, scale1, scale2));

        if (state != null) state.captureLimit(axis, min, max);
    }

    @WrapMethod(method = "lockAxes", remap = false)
    private void pym$lockAxes(ConstraintJointAxis[] axes, Operation<Void> original) {
        original.call((Object) axes);
        if (this.pym$state != null) this.pym$state.captureLockedAxes(axes);
    }
}
