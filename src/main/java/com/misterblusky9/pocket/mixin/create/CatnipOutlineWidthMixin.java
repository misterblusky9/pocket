package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.SubLevelOutlineScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.Outline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Outline.class, remap = false)
public abstract class CatnipOutlineWidthMixin {
    @ModifyVariable(
            method = "bufferCuboidLine(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Vector3f;"
                    + "Lnet/minecraft/core/Direction;FFLorg/joml/Vector4f;IZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false,
            require = 1
    )
    private float pocket$normalizeSubLevelLineWidth(
            final float width,
            @Local(argsOnly = true) final PoseStack.Pose pose
    ) {
        return SubLevelOutlineScale.normalize(width, pose.pose());
    }
}
