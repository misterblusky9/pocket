package com.misterblusky9.pym.internal.mixin.sable;

import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelSerializer.class, remap = false)
public abstract class HostedGhostMixin {
    @Inject(method = "fullyLoad", at = @At("RETURN"), remap = false)
    private static void pym$discardStaleCopy(ServerLevel level, SubLevelData data, CallbackInfoReturnable<ServerSubLevel> cir) {
        Hosted.loaded(cir.getReturnValue());
    }
}
