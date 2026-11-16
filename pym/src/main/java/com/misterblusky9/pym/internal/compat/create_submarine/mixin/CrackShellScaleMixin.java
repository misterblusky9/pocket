package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.client.SubLevelCrackRenderer", remap = false)
public abstract class CrackShellScaleMixin {
    @WrapOperation(
            method = "onRenderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 0
            ),
            remap = false
    )
    private static void pym$crackShellAtPoseScale(
            PoseStack poseStack,
            Quaternionf rotation,
            Operation<Void> original,
            @Local Pose3dc renderPose
    ) {
        original.call(poseStack, rotation);
        Vector3dc scale = renderPose.scale();
        if (DeepSeasScale.isFull(scale.x()) && DeepSeasScale.isFull(scale.y()) && DeepSeasScale.isFull(scale.z())) return;
        poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }
}
