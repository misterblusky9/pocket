package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.ShrinkRayHoverOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class ShrinkRayEntityOutlineMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void pocket$outlineShrinkRayTarget(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (ShrinkRayHoverOutline.outlines(entity)) cir.setReturnValue(true);
    }
}
