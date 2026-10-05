package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.SubLevelBlockEntityPass;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class SubLevelBlockEntityPassMixin {
    @Inject(method = "renderBlockEntities", at = @At("HEAD"), remap = false)
    private void pym$enterSubLevelBlockEntityPass(final CallbackInfo ci) {
        SubLevelBlockEntityPass.begin();
    }

    @Inject(method = "renderBlockEntities", at = @At("RETURN"), remap = false)
    private void pym$leaveSubLevelBlockEntityPass(final CallbackInfo ci) {
        SubLevelBlockEntityPass.end();
    }
}
