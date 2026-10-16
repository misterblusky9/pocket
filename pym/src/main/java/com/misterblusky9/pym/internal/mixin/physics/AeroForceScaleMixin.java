package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class AeroForceScaleMixin {
    @WrapOperation(
            method = "prePhysicsTick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;"
                            + "sable$contributeLiftAndDrag("
                            + "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderContext;"
                            + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                            + "Ldev/ryanhcode/sable/companion/math/Pose3d;"
                            + "DLorg/joml/Vector3dc;Lorg/joml/Vector3dc;"
                            + "Lorg/joml/Vector3d;Lorg/joml/Vector3d;"
                            + "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderGroup;)V"
            ),
            remap = false
    )
    private void pym$scaleAerofoilContribution(
            BlockSubLevelLiftProvider provider,
            BlockSubLevelLiftProvider.LiftProviderContext context,
            ServerSubLevel subLevel,
            Pose3d localPose,
            double timeStep,
            Vector3dc linearVelocity,
            Vector3dc angularVelocity,
            Vector3d linearImpulse,
            Vector3d angularImpulse,
            BlockSubLevelLiftProvider.LiftProviderGroup group,
            Operation<Void> original
    ) {
        double scale = ScaleState.getServerScale(subLevel);

        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            original.call(provider, context, subLevel, localPose, timeStep,
                    linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);
            return;
        }

        Vector3d linearBefore = new Vector3d(linearImpulse);
        Vector3d angularBefore = new Vector3d(angularImpulse);

        original.call(provider, context, subLevel, localPose, timeStep,
                linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);

        linearImpulse.sub(linearBefore).mul(scale).add(linearBefore);
        angularImpulse.sub(angularBefore).mul(scale).add(angularBefore);
    }
}
