package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.Mirrors;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class MirrorEvictionMixin {
    @Inject(method = "allocateSubLevel", at = @At("HEAD"), remap = false)
    private void pym$evictMirror(UUID uuid, int x, int z, Pose3d pose, CallbackInfoReturnable<SubLevel> cir) {
        SubLevelContainer container = (SubLevelContainer) (Object) this;
        if (!(container.getSubLevel(x, z) instanceof ClientSubLevel occupant)) return;
        if (Mirrors.isMirror(occupant)) {
            Mirrors.close(occupant);
            return;
        }
        LogUtils.getLogger().warn("Replacing stale client sub-level {} at plot {}, {} with {}",
                occupant.getUniqueId(), x, z, uuid);
        container.removeSubLevel(x, z, SubLevelRemovalReason.REMOVED);
    }
}
