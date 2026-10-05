package com.misterblusky9.pym.internal.compat.offroad.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.InternalForces;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
        targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity",
        remap = false
)
public abstract class WheelMountForceContextMixin {
    @ModifyExpressionValue(
            method = "sable$physicsTick(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/Pose3d;"
                            + "transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false,
            require = 0
    )
    private Vector3d pym$scaleSuspensionVelocity(
            final Vector3d localVelocity,
            final ServerSubLevel subLevel,
            final RigidBodyHandle handle,
            final double timeStep
    ) {
        final double scale = Pym.scale().of(subLevel);
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            return localVelocity;
        }

        localVelocity.y *= Math.sqrt(scale);
        return localVelocity;
    }

    @WrapOperation(
            method = "applyBatchedForces",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;"
                            + "applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"
            ),
            remap = false,
            require = 0
    )
    private void pym$flushWheelImpulseAsInternalForce(
            final RigidBodyHandle handle,
            final ForceTotal forceTotal,
            final Operation<Void> original
    ) {
        final SubLevel subLevel = Sable.HELPER.getContaining((BlockEntity) (Object) this);
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            original.call(handle, forceTotal);
            return;
        }

        pym$applySquareCubeFactor(serverSubLevel, forceTotal);

        try (InternalForces ignored = Pym.internalForces(serverSubLevel)) {
            original.call(handle, forceTotal);
        }
    }

    @Unique
    private static void pym$applySquareCubeFactor(
            final ServerSubLevel subLevel,
            final ForceTotal forceTotal
    ) {
        final double scale = Pym.scale().of(subLevel);
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return;

        forceTotal.getLocalForce().mul(scale);
        forceTotal.getLocalTorque().mul(scale);
    }
}
