package com.misterblusky9.pocket.compat.photomancy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.photomancy.PhotomancyCompat;
import dev.rew1nd.sableschematicapi.survival.camera.CameraCaptureService;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CameraCaptureService.class, remap = false)
public abstract class CameraCaptureHatMixin {
    @WrapOperation(
            method = "capture",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;isRemoved()Z"))
    private static boolean pocket$skipHats(ServerSubLevel body, Operation<Boolean> original) {
        return original.call(body) || PhotomancyCompat.isHat(body);
    }
}
