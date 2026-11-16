package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.system.ImplosionSequence", remap = false)
public abstract class ImplosionRadiusScaleMixin {
    @ModifyConstant(method = "blast", constant = @Constant(doubleValue = 14.0D), remap = false)
    private static double pym$damageRadiusAtHullScale(double radius) {
        return radius * DeepSeasScale.blastScale();
    }
}
