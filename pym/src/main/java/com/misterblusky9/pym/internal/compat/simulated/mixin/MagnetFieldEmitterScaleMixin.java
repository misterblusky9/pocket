package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.MagnetFieldEmitterScale;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets = "dev.simulated_team.simulated.content.blocks.redstone_magnet.RedstoneMagnetBlockEntity$MagnetParticleEmitter",
        remap = false
)
public abstract class MagnetFieldEmitterScaleMixin implements MagnetFieldEmitterScale {
    @Unique private static final double PYM$STEP = 0.2D;

    @Shadow @Final protected Vector3d pos;
    @Shadow @Final protected Vector3d newNudge;
    @Shadow @Final static Vector3d k1;
    @Shadow @Final static Vector3d k2;
    @Shadow @Final static Vector3d k3;
    @Shadow @Final static Vector3d k4;
    @Shadow @Final static Vector3d posTemp;

    @Shadow abstract void getField(Vector3d pos, Vector3d field);

    @Unique private double pym$scale = 1.0D;

    @Override
    public void pym$scale(double scale) {
        if (!(scale > 0.0D) || scale == this.pym$scale) return;
        this.pym$scale = scale;
        this.pym$step(this.pos, this.newNudge);
    }

    @Inject(method = "rk4", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$scaledStep(Vector3d at, Vector3d nudgeOut, CallbackInfo ci) {
        if (this.pym$scale == 1.0D) return;
        this.pym$step(at, nudgeOut);
        ci.cancel();
    }

    @ModifyConstant(method = "getField", constant = @Constant(doubleValue = 0.2D), remap = false)
    private double pym$scaleStopRadius(double radiusSqr) {
        return radiusSqr * this.pym$scale * this.pym$scale;
    }

    @Unique
    private void pym$step(Vector3d at, Vector3d nudgeOut) {
        double h = PYM$STEP * this.pym$scale;
        this.getField(at, k1);
        this.getField(at.fma(h / 2.0D, k1, posTemp), k2);
        this.getField(at.fma(h / 2.0D, k2, posTemp), k3);
        this.getField(at.fma(h, k3, posTemp), k4);
        nudgeOut.set(k1).fma(2.0D, k2).fma(2.0D, k3).add(k4).mul(h / 6.0D);
    }
}
