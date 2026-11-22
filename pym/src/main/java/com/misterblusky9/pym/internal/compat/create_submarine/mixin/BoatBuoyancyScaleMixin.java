package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.highseas.BoatBuoyancySystem", remap = false)
public abstract class BoatBuoyancyScaleMixin {
    @WrapOperation(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 0),
            remap = false
    )
    private static double pym$waterlineInCellUnits(
            double value,
            double min,
            double max,
            Operation<Double> original,
            @Local(argsOnly = true) ServerSubLevel subLevel
    ) {
        return original.call(DeepSeasScale.immersion(value, 0.5D, 1.0D, DeepSeasScale.of(subLevel)), min, max);
    }

    @WrapOperation(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyImpulseAtPoint(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"
            ),
            remap = false
    )
    private static void pym$liftAsInternalForce(RigidBodyHandle handle, Vector3dc point, Vector3dc impulse, Operation<Void> original) {
        DeepSeasScale.internally(handle, () -> original.call(handle, point, impulse));
    }

    @WrapOperation(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyLinearImpulse(Lorg/joml/Vector3dc;)V"
            ),
            remap = false
    )
    private static void pym$dragAsInternalForce(RigidBodyHandle handle, Vector3dc impulse, Operation<Void> original) {
        DeepSeasScale.internally(handle, () -> original.call(handle, impulse));
    }
}
