package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.HatFacePick;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class HatFacePickMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void pocket$grazeDummyHead(float partialTick, CallbackInfo ci) {
        HatFacePick.refine(partialTick);
    }
}
