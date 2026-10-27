package com.misterblusky9.pym.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.client.ParticleMotionScale;
import com.misterblusky9.pym.internal.client.ParticleScalePolicy;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ParticleEngine.class, priority = 500)
public abstract class ParticleTickScaleMixin {
    @WrapOperation(method = "tickParticle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;tick()V"))
    private void pym$tickInEmitterUnits(Particle particle, Operation<Void> original) {
        double scale = ((ParticleMotionScale.Carrier) particle).pym$getEmissionScale();
        if (!ParticleScalePolicy.valid(scale) || Math.abs(scale - 1.0D) < 1.0E-7D) {
            original.call(particle);
            return;
        }
        ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;
        motion.pym$setXd(motion.pym$getXd() / scale);
        motion.pym$setYd(motion.pym$getYd() / scale);
        motion.pym$setZd(motion.pym$getZd() / scale);
        try (ParticleMotionScale.Scope ignored = ParticleMotionScale.begin(particle, scale)) {
            original.call(particle);
        } finally {
            motion.pym$setXd(motion.pym$getXd() * scale);
            motion.pym$setYd(motion.pym$getYd() * scale);
            motion.pym$setZd(motion.pym$getZd() * scale);
        }
    }
}
