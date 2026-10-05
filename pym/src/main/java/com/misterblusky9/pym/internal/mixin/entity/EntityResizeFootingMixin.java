package com.misterblusky9.pym.internal.mixin.entity;

import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.misterblusky9.pym.internal.entity.ResizeFooting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityResizeFootingMixin {
    @Unique
    private AABB pym$footingBefore;
    @Unique
    private boolean pym$footingGrounded;

    @Inject(method = "refreshDimensions", at = @At("HEAD"))
    private void pym$rememberFooting(final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (!EntityScaleTracker.resizing(self)) return;
        this.pym$footingBefore = self.getBoundingBox();
        this.pym$footingGrounded = self.onGround();
    }

    @Inject(method = "refreshDimensions", at = @At("TAIL"))
    private void pym$keepFooting(final CallbackInfo ci) {
        final AABB before = this.pym$footingBefore;
        if (before == null) return;
        this.pym$footingBefore = null;
        ResizeFooting.keep((Entity) (Object) this, before, this.pym$footingGrounded);
    }
}
