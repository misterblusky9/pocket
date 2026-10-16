package com.misterblusky9.pocketdebug.mixin;

import com.simibubi.create.content.equipment.zapper.terrainzapper.CuboidBrush;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CuboidBrush.class, remap = false)
public abstract class WorldshaperCuboidMaxMixin {
    private static final int MAX = 128;

    @Inject(method = "getMax", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocketdebug$max(int paramIndex, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(MAX);
    }
}
