package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
        targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartBottomSliceRenderer",
        remap = false
)
public abstract class CoasterCartBottomSliceScaleMixin {
    @WrapOperation(
            method = "renderCell",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V"
            ),
            remap = false,
            require = 0
    )
    private static void pocket$applyCartScaleAfterBasis(
            final PoseStack poseStack,
            final Quaternionf rotation,
            final Operation<Void> original,
            @Local(ordinal = 0) final Pose3d visualPose
    ) {
        original.call(poseStack, rotation);

        final double scale = visualPose.scale().x();
        if (!PocketSized.isValidScale(scale) || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        final float s = (float) PocketSized.clampScale(scale);
        poseStack.scale(s, s, s);
    }
}
