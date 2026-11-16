package com.misterblusky9.pocket.compat.vista.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.vista.VistaHatFeed;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(targets = "net.mehvahdjukaar.vista.client.video_source.BroadcastVideoSource", remap = false)
public abstract class VistaBroadcastHatMixin {
    @WrapOperation(
            method = "getBroadcastContent",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/common/broadcast/BroadcastManager;getBroadcast(Ljava/util/UUID;Z)Lnet/mehvahdjukaar/vista/common/cassette/IBroadcastSource;"),
            remap = false)
    private IBroadcastSource pocket$unwornCameraIsOffline(BroadcastManager manager,
                                                           UUID uuid, boolean allowUnloaded,
                                                           Operation<IBroadcastSource> original) {
        IBroadcastSource source = original.call(manager, uuid, allowUnloaded);
        if (source instanceof ViewFinderBlockEntity viewfinder
                && VistaHatFeed.unavailable(VistaHatFeed.shown(viewfinder))) {
            return null;
        }
        return source;
    }
}
