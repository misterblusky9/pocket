package com.misterblusky9.pocket.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
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
    private void pocket$scaleAerofoilContribution(
            final BlockSubLevelLiftProvider provider,
            final BlockSubLevelLiftProvider.LiftProviderContext context,
            final ServerSubLevel subLevel,
            final Pose3d localPose,
            final double timeStep,
            final Vector3dc linearVelocity,
            final Vector3dc angularVelocity,
            final Vector3d linearImpulse,
            final Vector3d angularImpulse,
            final BlockSubLevelLiftProvider.LiftProviderGroup group,
            final Operation<Void> original
    ) {
        final double scale = ScaleState.getServerScale(subLevel);

        if (!PocketSized.isValidScale(scale) || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            original.call(provider, context, subLevel, localPose, timeStep,
                    linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);
            return;
        }

        final Vector3d linearBefore = new Vector3d(linearImpulse);
        final Vector3d angularBefore = new Vector3d(angularImpulse);

        original.call(provider, context, subLevel, localPose, timeStep,
                linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);

        linearImpulse.sub(linearBefore).mul(scale).add(linearBefore);
        angularImpulse.sub(angularBefore).mul(scale).add(angularBefore);
    }
}
