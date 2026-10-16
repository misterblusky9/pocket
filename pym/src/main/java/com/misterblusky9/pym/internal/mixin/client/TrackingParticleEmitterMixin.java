package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TrackingEmitter.class)
public abstract class TrackingParticleEmitterMixin {
    @Shadow @Final private Entity entity;

    @WrapMethod(method = "tick")
    private void pym$particleSourceForTrackingEmitter(Operation<Void> original) {
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromEntity(this.entity)) {
            original.call();
        }
    }
}
