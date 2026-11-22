package com.misterblusky9.pym.internal.mixin.sable;

import com.misterblusky9.pym.api.Detached;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class DetachedAdoptEntitiesMixin {
    @Inject(method = "deleteAllEntities", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$keepAdopted(CallbackInfo ci) {
        if (Detached.adopting((ServerSubLevel) (Object) this)) ci.cancel();
    }
}
