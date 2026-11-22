package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.MagnifyingGlassLens;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MagnifyingGlassHandPostMixin {
    @Inject(method = "renderItemInHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V", at = @At("TAIL"))
    private void pocket$renderMagnifyingLens(Camera camera, float partialTick,
                                             Matrix4f projectionMatrix, CallbackInfo ci) {
        MagnifyingGlassLens.afterFirstPersonHands(partialTick);
    }
}
