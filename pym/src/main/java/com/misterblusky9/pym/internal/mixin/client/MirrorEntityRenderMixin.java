package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.Mirrors;
import com.misterblusky9.pym.internal.client.PortraitScope;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MirrorEntityRenderMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void pym$portraitDrawsMirrorEntities(Entity entity, Frustum frustum, double x, double y, double z,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!PortraitScope.active() && Mirrors.holds(entity)) cir.setReturnValue(false);
    }
}
