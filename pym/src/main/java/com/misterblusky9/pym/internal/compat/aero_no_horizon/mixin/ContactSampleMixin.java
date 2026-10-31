package com.misterblusky9.pym.internal.compat.aero_no_horizon.mixin;

import com.misterblusky9.pym.internal.compat.aero_no_horizon.ScaledContactSample;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.github.aero_no_horizon.trackwork.content.track.SableTrackPhysicsHelper$ContactSample", remap = false)
public abstract class ContactSampleMixin implements ScaledContactSample {
    @Unique private double pym$scale = 1.0D;

    @Override
    public void pym$setScale(double scale) {
        this.pym$scale = Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }

    @ModifyReturnValue(method = "hitDistance()D", at = @At("RETURN"), remap = false)
    private double pym$localDistance(double distance) {
        return distance / (this.pym$scale > 0.0D ? this.pym$scale : 1.0D);
    }
}
