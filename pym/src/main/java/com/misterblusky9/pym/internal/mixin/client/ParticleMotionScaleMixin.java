package com.misterblusky9.pym.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.client.ParticleMotionScale;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Applies the emitter scale to intrinsic displacement at the common Particle
 * move boundary, before Sable performs collisions against world geometry.
 * The normalization scope is ONLY active during Particle.tick(); Sable's
 * inherited plot motion happens outside that scope and is not multiplied.
 */
@Mixin(value = Particle.class, priority = 1100)
public abstract class ParticleMotionScaleMixin implements ParticleMotionScale.Carrier {
    @Unique private double pym$emissionScale = 1.0D;
    @Unique private boolean pym$initialized;
    @Unique private boolean pym$applied;

    @Override public double pym$getEmissionScale() { return pym$emissionScale; }
    @Override public void pym$setEmissionScale(double scale) { pym$emissionScale = scale; }
    @Override public boolean pym$isInitialized() { return pym$initialized; }
    @Override public void pym$setInitialized(boolean initialized) { pym$initialized = initialized; }
    @Override public boolean pym$isApplied() { return pym$applied; }
    @Override public void pym$setApplied(boolean applied) { pym$applied = applied; }

    @WrapMethod(method = "move")
    private void pym$scaleIntrinsicMovement(double x, double y, double z, Operation<Void> original) {
        double scale = ParticleMotionScale.movementScale((Particle) (Object) this);
        original.call(x * scale, y * scale, z * scale);
    }
}
