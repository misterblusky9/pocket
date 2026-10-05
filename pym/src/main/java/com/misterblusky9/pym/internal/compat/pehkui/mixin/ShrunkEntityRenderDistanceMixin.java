package com.misterblusky9.pym.internal.compat.pehkui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(Entity.class)
public abstract class ShrunkEntityRenderDistanceMixin {
    @ModifyExpressionValue(
            method = "shouldRenderAtSqrDistance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getSize()D")
    )
    private double pym$cullShrunkEntitiesAtFullSize(final double original) {
        final Entity self = (Entity) (Object) this;
        final float width = ScaleUtils.getBoundingBoxWidthScale(self);
        final float height = ScaleUtils.getBoundingBoxHeightScale(self);
        if (!(width > 0.0F) || !(height > 0.0F) || !Float.isFinite(width) || !Float.isFinite(height)
                || (width >= 1.0F && height >= 1.0F)) {
            return original;
        }
        final AABB box = self.getBoundingBox();
        return (box.getXsize() / width + box.getZsize() / width + box.getYsize() / height) / 3.0D;
    }
}
