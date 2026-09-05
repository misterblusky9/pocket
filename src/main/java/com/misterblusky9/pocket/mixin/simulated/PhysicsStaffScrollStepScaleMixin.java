package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.simulated.PhysicsStaffScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
        targets = "dev.simulated_team.simulated.content.physics_staff."
                + "PhysicsStaffClientHandler$PhysicsStaffMouseHandler",
        remap = false
)
public abstract class PhysicsStaffScrollStepScaleMixin {
    @WrapOperation(
            method = "onScroll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"),
            remap = false,
            require = 1
    )
    private double pocket$scaleScrollStep(
            final double value,
            final double min,
            final double max,
            final Operation<Double> original
    ) {
        final double scale = PhysicsStaffScale.dragScale();
        if (scale >= 1.0D) {
            return original.call(value, min, max);
        }

        final double inSubLevelFrame = value / Math.sqrt(scale);
        return original.call(inSubLevelFrame, min, max) * scale;
    }
}
