package com.misterblusky9.pocket.mixin.entity;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.EntityScaleTracker;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDimensionsScaleMixin {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void pocket$scaleLivingDimensions(
            final Pose pose,
            final CallbackInfoReturnable<EntityDimensions> cir
    ) {
        final LivingEntity self = (LivingEntity) (Object) this;
        final double scale = EntityScaleTracker.dimensionScale(self);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;
        cir.setReturnValue(EntityScaleTracker.applyScale(self, cir.getReturnValue(), scale));
    }
}
