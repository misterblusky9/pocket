package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = SubLevelTrackingSystem.class, remap = false, priority = 900)
public abstract class HostedTrackingMixin {
    @ModifyExpressionValue(
            method = {"tick", "sendBoundsUpdates", "sendMovementUpdates"},
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;getAllSubLevels()Ljava/util/List;"),
            remap = false
    )
    private List<? extends SubLevel> pym$hideHosted(List<? extends SubLevel> original) {
        return Hosted.visible(original);
    }

    @Inject(method = "sendFullSync", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$noHostedSync(ServerPlayer player, ServerSubLevel subLevel,
                                  @Nullable CustomPacketPayload extraPacket, CallbackInfo ci) {
        if (Hosted.isHosted(subLevel)) ci.cancel();
    }

    @Inject(method = "onSubLevelRemoved", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$noHostedRemoval(SubLevel subLevel, SubLevelRemovalReason reason, CallbackInfo ci) {
        if (Hosted.isHosted(subLevel)) ci.cancel();
    }
}
