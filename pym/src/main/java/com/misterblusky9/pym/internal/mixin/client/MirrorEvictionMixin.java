package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.Mirrors;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class MirrorEvictionMixin {
    @Inject(method = "allocateSubLevel", at = @At("HEAD"), remap = false)
    private void pym$evictMirror(UUID uuid, int x, int z, Pose3d pose, CallbackInfoReturnable<SubLevel> cir) {
        if (((SubLevelContainer) (Object) this).getSubLevel(x, z) instanceof ClientSubLevel mirror
                && Mirrors.isMirror(mirror)) Mirrors.close(mirror);
    }
}
