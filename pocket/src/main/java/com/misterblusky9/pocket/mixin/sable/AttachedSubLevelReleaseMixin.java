package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pym.api.Detached;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubLevelPhysicsSystem.class)
public abstract class AttachedSubLevelReleaseMixin {
    @Inject(method = "onSubLevelRemoved", at = @At("HEAD"))
    private void pocket$release(SubLevel subLevel, SubLevelRemovalReason reason, CallbackInfo ci) {
        if (subLevel instanceof ServerSubLevel server) {
            if (reason == SubLevelRemovalReason.REMOVED) {
                var store = com.misterblusky9.pocket.compat.simulated.WeldStore.get(server.getLevel());
                for (var weld : Detached.adopting(server) ? store.touching(server.getUniqueId())
                        : store.removeAllTouching(server.getUniqueId())) {
                    com.misterblusky9.pocket.compat.simulated.WeldRuntime.drop(weld.weldId());
                }
                com.misterblusky9.pocket.compat.simulated.CrossScaleWelds.refreshLoaded(server.getLevel());
            }
        }
    }
}
