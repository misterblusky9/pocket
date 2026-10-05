package com.misterblusky9.pym.internal.mixin.entity;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDimensionsScaleMixin {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void pym$scaleDimensions(
            final Pose pose,
            final CallbackInfoReturnable<EntityDimensions> cir
    ) {
        final Entity self = (Entity) (Object) this;
        final double scale = EntityScaleTracker.dimensionScale(self);
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return;
        cir.setReturnValue(EntityScaleTracker.applyScale(cir.getReturnValue(), scale));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void pym$updateInheritedScale(final CallbackInfo ci) {
        EntityScaleTracker.tick((Entity) (Object) this);
    }
}
