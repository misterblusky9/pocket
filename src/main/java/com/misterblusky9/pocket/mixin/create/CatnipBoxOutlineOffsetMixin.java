package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.SubLevelOutlineScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.AABBOutline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = AABBOutline.class, remap = false)
public abstract class CatnipBoxOutlineOffsetMixin {
    @ModifyVariable(
            method = "renderBox",
            at = @At("STORE"),
            ordinal = 0,
            remap = false,
            require = 1
    )
    private float pocket$normalizeSurfaceOffset(
            final float inflate,
            @Local(argsOnly = true) final PoseStack poseStack
    ) {
        return SubLevelOutlineScale.normalize(inflate, poseStack.last().pose());
    }
}
