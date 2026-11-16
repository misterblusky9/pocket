package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer", remap = false)
public abstract class WaterCullRadiusScaleMixin {
    @ModifyReturnValue(method = "computeRadius", at = @At("RETURN"), remap = false)
    private static double pym$radiusInWorldUnits(double radius, @Local(argsOnly = true) SubLevel sub) {
        return radius * DeepSeasScale.of(sub);
    }
}
