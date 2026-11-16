package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.maxenonyme.highseas.block.entity.BuoyBlockEntity", remap = false)
public abstract class BuoyWaterlineScaleMixin {
    @WrapOperation(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 0),
            remap = false
    )
    private double pym$immersionInCellUnits(
            double value,
            double min,
            double max,
            Operation<Double> original,
            @Local(argsOnly = true) ServerSubLevel subLevel
    ) {
        return original.call(DeepSeasScale.immersion(value, 1.5D, 1.5D, DeepSeasScale.of(subLevel)), min, max);
    }

    @ModifyConstant(method = "sable$physicsTick", constant = @Constant(doubleValue = -0.15D), remap = false)
    private double pym$rideHeightInCellUnits(double rideHeight) {
        return rideHeight * DeepSeasScale.of(Sable.HELPER.getContaining((BlockEntity) (Object) this));
    }
}
