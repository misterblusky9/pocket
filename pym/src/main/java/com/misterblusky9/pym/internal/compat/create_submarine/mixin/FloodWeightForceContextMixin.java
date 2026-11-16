package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.compartment.FloodSystem", remap = false)
public abstract class FloodWeightForceContextMixin {
    @WrapOperation(
            method = "onPhysicsTick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyImpulseAtPoint(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"
            ),
            remap = false
    )
    private static void pym$floodWeightAsInternalForce(RigidBodyHandle handle, Vector3dc point, Vector3dc impulse, Operation<Void> original) {
        DeepSeasScale.internally(handle, () -> original.call(handle, point, impulse));
    }
}
