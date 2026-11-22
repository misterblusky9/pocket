package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.HatFacePick;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class HatFacePlaceMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void pocket$aimAtHatCell(CallbackInfo ci) {
        HatFacePick.beginUse();
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void pocket$restoreAim(CallbackInfo ci) {
        HatFacePick.endUse();
    }
}
