package com.misterblusky9.pocket.compat.vista.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.compat.vista.VistaViewFinderScale;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = VistaLevelRenderer.class, remap = false)
public abstract class VistaFeedNearPlaneMixin {
    @Unique
    private static double pocket$feedScale = 1.0D;

    @WrapOperation(
            method = "doRender",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/client/renderer/VistaLevelRenderer;renderLevel(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V"),
            remap = false)
    private static void pocket$trackFeedScale(Minecraft minecraft, RenderTarget target, Camera camera,
                                              float fov, Matrix4f projection, Operation<Void> original,
                                              @Local(argsOnly = true) BlockEntity source) {
        double previous = pocket$feedScale;
        pocket$feedScale = VistaViewFinderScale.of(source);
        try {
            original.call(minecraft, target, camera, fov, projection);
        } finally {
            pocket$feedScale = previous;
        }
    }

    @ModifyArg(
            method = "createProjectionMatrixForCamera",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"),
            index = 2,
            remap = false)
    private static float pocket$feedNearPlane(float near) {
        return VistaViewFinderScale.near(near, pocket$feedScale);
    }
}
