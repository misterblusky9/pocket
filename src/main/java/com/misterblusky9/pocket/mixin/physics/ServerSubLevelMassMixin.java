package com.misterblusky9.pocket.mixin.physics;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.physics.ScaledMassData;
import com.misterblusky9.pocket.physics.MassScaleContext;
import com.misterblusky9.pocket.scale.ScaleState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelMassMixin {
    @Inject(method = "getMassTracker", at = @At("RETURN"), cancellable = true, remap = false)
    private void pocket$scaledMassView(final CallbackInfoReturnable<MassData> cir) {
        final MassData raw = cir.getReturnValue();
        if (raw == null || raw instanceof ScaledMassData) return;

        final ServerSubLevel self = (ServerSubLevel) (Object) this;
        if (!MassScaleContext.activeFor(self)) return;
        final double scale = ScaleState.getServerScale(self);

        if (!Double.isFinite(scale) || scale <= 0.0D) return;

        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            final double mass = raw.getMass();
            if (!(mass > 0.0D) || mass >= ScaledMassData.SUPERLIGHT_MASS) return;
            cir.setReturnValue(new ScaledMassData(raw, 1.0D));
            return;
        }

        cir.setReturnValue(new ScaledMassData(raw, scale));
    }
}
