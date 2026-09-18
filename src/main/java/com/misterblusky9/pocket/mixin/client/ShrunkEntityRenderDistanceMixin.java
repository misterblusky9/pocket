package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class ShrunkEntityRenderDistanceMixin {
    @ModifyExpressionValue(
            method = "shouldRenderAtSqrDistance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getSize()D")
    )
    private double pocket$cullShrunkEntitiesAtFullSize(final double original) {
        if (!PehkuiScaleBridge.ownsScaling()) return original;
        return PehkuiScaleBridge.unscaledBoxSize((Entity) (Object) this, original);
    }
}
