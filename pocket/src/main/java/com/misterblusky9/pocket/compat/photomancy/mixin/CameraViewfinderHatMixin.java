package com.misterblusky9.pocket.compat.photomancy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.photomancy.PhotomancyCompat;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.rew1nd.sableschematicapi.survival.camera.client.CameraViewfinderCandidates", remap = false)
public abstract class CameraViewfinderHatMixin {
    @WrapOperation(
            method = "find",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/SubLevel;isRemoved()Z"))
    private static boolean pocket$skipHats(SubLevel body, Operation<Boolean> original) {
        return original.call(body) || PhotomancyCompat.isHat(body);
    }
}
