package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.highseas.block.entity.AnchorBlockEntity", remap = false)
public abstract class AnchorFloorProbeScaleMixin {
    @WrapOperation(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"),
            remap = false
    )
    private BlockPos pym$probeInCellUnits(
            double x,
            double y,
            double z,
            Operation<BlockPos> original,
            @Local(argsOnly = true) ServerSubLevel subLevel,
            @Local Vector3d center
    ) {
        double scale = DeepSeasScale.of(subLevel);
        if (DeepSeasScale.isFull(scale)) return original.call(x, y, z);
        return original.call(x, center.y - (center.y - y) * scale, z);
    }
}
