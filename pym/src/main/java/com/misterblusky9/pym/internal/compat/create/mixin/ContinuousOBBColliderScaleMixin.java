package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.ContraptionCollisionScaleContext;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ContinuousOBBCollider.class, remap = false)
public abstract class ContinuousOBBColliderScaleMixin {
    @ModifyConstant(
            method = "collideMany",
            constant = @Constant(doubleValue = 0.5D),
            require = 3
    )
    private static double pym$scaleBroadphasePadding(final double original) {
        return original * ContraptionCollisionScaleContext.currentScale();
    }
}
