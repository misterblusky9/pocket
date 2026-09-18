package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.ShrinkRayPick;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftShrinkRayPickMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true, require = 1)
    private void pocket$pickAimedScale(final CallbackInfoReturnable<Boolean> cir) {
        if (ShrinkRayPick.tryPick()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true, require = 1)
    private void pocket$holdDoesNotBreakCraft(final boolean leftClick, final CallbackInfo ci) {
        if (ShrinkRayPick.aiming()) ci.cancel();
    }
}
