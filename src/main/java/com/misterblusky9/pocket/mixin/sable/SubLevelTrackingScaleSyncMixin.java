package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pocket.network.ScaleNetwork;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SubLevelTrackingScaleSyncMixin {
    @Shadow private int interpolationTick;

    @Inject(method = "sendFullSync", at = @At("HEAD"), remap = false)
    private void pocket$sendScaleBeforeTracking(
            final ServerPlayer player,
            final ServerSubLevel subLevel,
            @Nullable final CustomPacketPayload extraPacket,
            final CallbackInfo ci
    ) {
        ScaleNetwork.sendTrackingScale(player, subLevel, this.interpolationTick);
    }
}
