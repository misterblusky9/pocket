package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity", remap = false)
public abstract class BoatEngineImmersionScaleMixin {
    @WrapOperation(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 0),
            remap = false
    )
    private double pym$propellerDepthInCellUnits(
            double value,
            double min,
            double max,
            Operation<Double> original,
            @Local(argsOnly = true) ServerSubLevel subLevel
    ) {
        return original.call(DeepSeasScale.immersion(value, 1.0D, 1.5D, DeepSeasScale.of(subLevel)), min, max);
    }
}
