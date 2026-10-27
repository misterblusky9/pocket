package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageForceContext;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageLengthScale;
import com.misterblusky9.pym.internal.physics.GenericConstraintState;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity", remap = false)
public abstract class HydraulicConstraintMixin {
    @WrapOperation(
            method = "applyLengthImpulse",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/"
                    + "RigidBodyHandle;applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"),
            remap = false
    )
    private void pym$scaleBothHydraulicEndpoints(
            RigidBodyHandle handle, ForceTotal force, Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) ServerSubLevel own,
            @Local(argsOnly = true, ordinal = 1) ServerSubLevel other
    ) {
        LinkageForceContext.apply(handle, force, own, other, original);
    }

    @WrapOperation(
            method = "refreshGiantHydraulicMotor",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/constraint/"
                    + "GenericConstraintHandle;setLimit(Ldev/ryanhcode/sable/api/physics/constraint/"
                    + "ConstraintJointAxis;DD)V"),
            remap = false
    )
    private void pym$metricLengthLimits(
            GenericConstraintHandle handle, ConstraintJointAxis axis,
            double min, double max, Operation<Void> original
    ) {
        if (handle instanceof GenericConstraintState.Access access
                && access.pym$genericState() != null) {
            access.pym$genericState().useMetricLinearLimits();
        }
        double scale = LinkageLengthScale.ofLink((BlockEntity) (Object) this);
        original.call(handle, axis, min * scale, max * scale);
    }
}
