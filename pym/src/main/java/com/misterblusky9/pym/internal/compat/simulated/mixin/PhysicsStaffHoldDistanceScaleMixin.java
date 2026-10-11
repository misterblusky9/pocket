package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.simulated.PhysicsStaffScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffHoldDistanceScaleMixin {
    @WrapOperation(
            method = "clampDistance",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;clamp(DDD)D"),
            remap = false,
            require = 1
    )
    private double pym$sizedNearHoldLimit(
            final double value,
            final double min,
            final double max,
            final Operation<Double> original
    ) {
        return original.call(value, PhysicsStaffScale.minHoldDistance(min, max), max);
    }
}
