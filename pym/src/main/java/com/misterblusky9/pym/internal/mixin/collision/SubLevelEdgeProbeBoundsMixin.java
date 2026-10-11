package com.misterblusky9.pym.internal.mixin.collision;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CanFallAtleastHelper.class, remap = false)
public abstract class SubLevelEdgeProbeBoundsMixin {
    @Redirect(
            method = "canFallAtleastWithSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"
            ),
            remap = false
    )
    private static Iterable<BlockPos> pym$clampProbeToPlot(
            final BlockPos min,
            final BlockPos max,
            @Local(name = "subLevel") final SubLevel subLevel
    ) {
        return PlotScanBounds.betweenClosed(
                subLevel,
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ()
        );
    }
}
