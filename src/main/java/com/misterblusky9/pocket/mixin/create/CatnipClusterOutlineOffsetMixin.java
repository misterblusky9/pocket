package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.SubLevelOutlineScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.BlockClusterOutline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = BlockClusterOutline.class, remap = false)
public abstract class CatnipClusterOutlineOffsetMixin {
    @ModifyConstant(
            method = "bufferBlockFace",
            constant = @Constant(floatValue = 128.0F),
            remap = false,
            require = 1
    )
    private float pocket$normalizeFaceOffset(
            final float divisor,
            @Local(argsOnly = true) final PoseStack.Pose pose
    ) {
        return SubLevelOutlineScale.scaleDivisor(divisor, pose.pose());
    }
}
