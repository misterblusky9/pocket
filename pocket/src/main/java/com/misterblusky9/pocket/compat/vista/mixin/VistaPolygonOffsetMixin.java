package com.misterblusky9.pocket.compat.vista.mixin;

import com.misterblusky9.pocket.client.HeadwearUnderHat;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSystem.class, remap = false)
public abstract class VistaPolygonOffsetMixin {
    @Inject(method = "enablePolygonOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pocket$skipUnderHat(CallbackInfo ci) {
        if (HeadwearUnderHat.polygonOffsetSuppressed()) ci.cancel();
    }
}
