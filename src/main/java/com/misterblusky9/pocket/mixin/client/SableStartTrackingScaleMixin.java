package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientboundStartTrackingSubLevelPacket.class, remap = false)
public abstract class SableStartTrackingScaleMixin {
    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private void pocket$restoreInitialScale(final CallbackInfo ci) {
        final ClientboundStartTrackingSubLevelPacket packet =
                (ClientboundStartTrackingSubLevelPacket) (Object) this;
        if (!ScaleState.hasClientSnapshot(packet.subLevelID())) return;

        final double scale = ScaleState.getClientScale(packet.subLevelID());
        if (!PocketSized.isValidScale(scale)) return;

        if (packet.lastPose() instanceof final Pose3d lastPose) {
            lastPose.scale().set(scale, scale, scale);
        }
        packet.pose().scale().set(scale, scale, scale);
    }
}
