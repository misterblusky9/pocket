package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.compat.create.ContraptionCollisionScaleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider$ContinuousSeparationManifold", remap = false)
public abstract class ContinuousOBBManifoldScaleMixin {
    @ModifyConstant(
            method = "separate",
            constant = @Constant(doubleValue = 0.125D),
            require = 1
    )
    private static double pocket$scaleContactInset(final double original) {
        return original * ContraptionCollisionScaleContext.currentScale();
    }

    @ModifyConstant(
            method = "withSignedEpsilon",
            constant = @Constant(doubleValue = 1.0E-4D),
            require = 1
    )
    private static double pocket$scaleSeparationEpsilon(final double original) {
        return original * ContraptionCollisionScaleContext.currentScale();
    }
}
