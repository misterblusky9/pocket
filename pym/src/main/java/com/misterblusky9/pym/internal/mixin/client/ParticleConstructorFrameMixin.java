package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.misterblusky9.pym.internal.client.ParticleScalePolicy;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable 2.0.6 removes vanilla's fixed +0.1 constructor Y and reinserts it as
 * WORLD-up 0.1 when the particle starts in a plot. Fix this at its origin,
 * independent of particle class and before any provider/subclass modifies
 * the resulting velocity. Must run after Sable's default-priority TAIL hook.
 */
@Mixin(value = Particle.class, priority = 500)
public abstract class ParticleConstructorFrameMixin {
    @Shadow protected double xd;
    @Shadow protected double yd;
    @Shadow protected double zd;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V", at = @At("TAIL"))
    private void pym$fixSubLevelConstructorImpulse(
            ClientLevel level, double x, double y, double z,
            double xd, double yd, double zd, CallbackInfo ci) {
        // TerrainParticle uses Particle.setPower after construction (and Sable
        // temporarily converts its velocity to world space during block-crack
        // creation). Preserve the previously proven post-projection correction
        // for that shared particle behavior instead of correcting it twice.
        if ((Object) this instanceof TerrainParticle) return;
        ClientSubLevel plot = Sable.HELPER.getContainingClient(new Vec3(x, y, z));
        if (plot == null || plot.isRemoved()) return;
        double correction = ParticleScalePolicy.constructorWorldLiftAdjustment(
                ParticleEmitterSource.scaleOf(plot));
        if (correction == 0.0D) return;
        Vec3 local = plot.logicalPose().transformNormalInverse(new Vec3(0.0D, correction, 0.0D));
        this.xd += local.x;
        this.yd += local.y;
        this.zd += local.z;
    }
}
