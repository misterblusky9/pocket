package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.ShrinkRayHoverOutline;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ShrinkRayEntityOutlineColourMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void pocket$outlineColour(final CallbackInfoReturnable<Integer> cir) {
        final Entity entity = (Entity) (Object) this;
        if (ShrinkRayHoverOutline.outlines(entity)) cir.setReturnValue(ShrinkRayHoverOutline.entityColour(entity));
    }
}
