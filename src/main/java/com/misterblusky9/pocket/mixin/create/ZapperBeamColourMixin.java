package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.PocketBeamColours;
import com.misterblusky9.pocket.client.PocketLaserBeamColour;
import com.simibubi.create.content.equipment.zapper.ZapperRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ZapperRenderHandler.class, remap = false)
public abstract class ZapperBeamColourMixin {
    @Inject(method = "addBeam", at = @At("HEAD"), require = 1)
    private void pocket$tagBeamColour(
            final ZapperRenderHandler.LaserBeam beam,
            final CallbackInfo ci
    ) {
        if (beam instanceof final PocketLaserBeamColour coloured) {
            coloured.pocket$colour(PocketBeamColours.take(coloured.pocket$end()));
        }
    }

    @ModifyConstant(
            method = "lambda$tick$1(Lcom/simibubi/create/content/equipment/zapper/ZapperRenderHandler$LaserBeam;)V",
            constant = @Constant(intValue = 0xFFFFFF),
            require = 1
    )
    private static int pocket$beamColour(
            final int original,
            @Local(argsOnly = true) final ZapperRenderHandler.LaserBeam beam
    ) {
        return beam instanceof final PocketLaserBeamColour coloured ? coloured.pocket$colour() : original;
    }
}
