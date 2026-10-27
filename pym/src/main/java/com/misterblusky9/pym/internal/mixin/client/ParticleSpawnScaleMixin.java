package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.misterblusky9.pym.internal.client.ParticleMotionScale;
import com.misterblusky9.pym.internal.client.ParticleScalePolicy;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures ownership BEFORE Sable's add hook projects plot coordinates. */
@Mixin(value = ParticleEngine.class, priority = 1100)
public abstract class ParticleSpawnScaleMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void pym$captureEmitterBeforeProjection(Particle particle, CallbackInfo ci) {
        ParticleMotionScale.Carrier state = (ParticleMotionScale.Carrier) particle;
        if (state.pym$isInitialized()) return;
        ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;
        ClientSubLevel plot = Sable.HELPER.getContainingClient(
                new Vec3(motion.pym$getX(), motion.pym$getY(), motion.pym$getZ()));
        double plotScale = plot != null && !plot.isRemoved()
                ? ParticleEmitterSource.scaleOf(plot) : 1.0D;
        double explicit = ParticleEmitterSource.currentScale();
        state.pym$setEmissionScale(ParticleScalePolicy.emitterScale(explicit,
                plot == null ? Double.NaN : plotScale));
        state.pym$setInitialized(true);
    }

    @Inject(method = "add", at = @At("TAIL"))
    private void pym$scaleAfterSableProjection(Particle particle, CallbackInfo ci) {
        ParticleMotionScale.Carrier state = (ParticleMotionScale.Carrier) particle;
        if (!state.pym$isInitialized() || state.pym$isApplied()) return;
        state.pym$setApplied(true);
        double emitter = state.pym$getEmissionScale();
        ParticleExtension extension = (ParticleExtension) particle;
        // Idempotent; Sable normally calls this at the start of add().
        extension.sable$initialKickOut();
        SubLevel tracking = extension.sable$getTrackingSubLevel();
        double projection = tracking instanceof ClientSubLevel plot && !plot.isRemoved()
                ? ParticleEmitterSource.scaleOf(plot) : 1.0D;
        double ratio = ParticleScalePolicy.remainingVelocityRatio(emitter, projection);
        if (!Double.isFinite(ratio) || ratio <= 0.0D) return;
        ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;
        // Keep the existing successful debris correction at the point where
        // Sable has already projected velocity. This is a TerrainParticle
        // setPower compatibility rule shared by EVERY breaking block, not a
        // handler for a particular block, particle texture, or emitter.
        if (particle instanceof TerrainParticle && tracking instanceof ClientSubLevel) {
            motion.pym$setYd(motion.pym$getYd()
                    + ParticleScalePolicy.terrainLegacyWorldLiftAdjustment(projection));
        }
        if (Math.abs(ratio - 1.0D) > 1.0E-7D) {
            motion.pym$setXd(motion.pym$getXd() * ratio);
            motion.pym$setYd(motion.pym$getYd() * ratio);
            motion.pym$setZd(motion.pym$getZd() * ratio);
        }
        if (Math.abs(emitter - 1.0D) > 1.0E-7D) {
            particle.scale((float) emitter);
        }
    }

    /** Particle providers may add world-space jitter after Level selected a source position. */
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void pym$scaleProviderLocalOffset(
            ParticleOptions options, double x, double y, double z,
            double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        if (particle == null) return;
        double explicit = ParticleEmitterSource.currentScale();
        if (!ParticleScalePolicy.valid(explicit) || Math.abs(explicit - 1.0D) < 1.0E-7D) return;
        // A plotted particle was projected by Sable. Its source and final
        // positions are now in DIFFERENT coordinate frames; never subtract them.
        if (((ParticleExtension) particle).sable$getTrackingSubLevel() != null) return;
        ParticleMotionAccessor m = (ParticleMotionAccessor) particle;
        double x0 = m.pym$getX();
        double y0 = m.pym$getY();
        double z0 = m.pym$getZ();
        double nx = x + (x0 - x) * explicit;
        double ny = y + (y0 - y) * explicit;
        double nz = z + (z0 - z) * explicit;
        if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz)) return;
        particle.setPos(nx, ny, nz);
        m.pym$setXo(m.pym$getXo() + nx - x0);
        m.pym$setYo(m.pym$getYo() + ny - y0);
        m.pym$setZo(m.pym$getZo() + nz - z0);
    }
}
