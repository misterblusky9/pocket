package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.simulated.PhysicsStaffScale;
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
    private double pym$scaleScrollStep(
            final double value,
            final double min,
            final double max,
            final Operation<Double> original
    ) {
        final double frame = PhysicsStaffScale.scrollFrame();
        if (frame == 1.0D) {
            return original.call(value, min, max);
        }

        return original.call(value / Math.sqrt(frame), min, max) * frame;
    }
}
