package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageForceContext;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity", remap = false)
public abstract class UniversalJointForceMixin {
    @WrapOperation(
            method = "applyElasticImpulse",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/"
                    + "RigidBodyHandle;applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"),
            remap = false
    )
    private void pym$scaleBothElasticEndpoints(
            RigidBodyHandle handle, ForceTotal force, Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) ServerSubLevel own,
            @Local(argsOnly = true, ordinal = 1) ServerSubLevel other
    ) {
        LinkageForceContext.apply(handle, force, own, other, original);
    }
}
