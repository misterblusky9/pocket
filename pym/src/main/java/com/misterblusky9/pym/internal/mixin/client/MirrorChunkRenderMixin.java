package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.Mirrors;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class MirrorChunkRenderMixin {
    @Inject(method = "isSingleBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pym$mirrorsDrawAsChunks(ClientSubLevel subLevel, CallbackInfoReturnable<Boolean> cir) {
        if (Mirrors.isMirror(subLevel)) cir.setReturnValue(false);
    }
}
