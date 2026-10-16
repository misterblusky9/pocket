package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleSpawnScaleMixin {
    @Unique private static int pym$debugCount; // TEMP-PARTICLE-DEBUG
    @Inject(method = "add", at = @At("TAIL"))
    private void pym$scaleParticle(Particle particle, CallbackInfo ci) {
        ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;
        if (motion.pym$getLevel() == null) return;

        Vec3 preKickPosition = new Vec3(
                motion.pym$getX(),
                motion.pym$getY(),
                motion.pym$getZ()
        );
        ClientSubLevel preKickOrigin = Sable.HELPER.getContainingClient(preKickPosition);

        ParticleExtension extension = (ParticleExtension) particle;
        extension.sable$initialKickOut();

        ClientSubLevel origin = null;
        SubLevel tracked = extension.sable$getTrackingSubLevel();
        if (tracked instanceof ClientSubLevel trackedClient && !trackedClient.isRemoved()) {
            origin = trackedClient;
        } else if (preKickOrigin != null && !preKickOrigin.isRemoved()) {
            origin = preKickOrigin;
        }

        double emitter = ParticleEmitterSource.currentScale();
        double uniform;
        if (ScaleBounds.isValid(emitter)) {
            uniform = emitter;
        } else if (origin != null) {
            uniform = pym$uniformScale(origin);
        } else {
            return;
        }

        // TEMP-PARTICLE-DEBUG
        if (particle instanceof net.minecraft.client.particle.FlameParticle
                || particle instanceof net.minecraft.client.particle.CampfireSmokeParticle
                || particle instanceof net.minecraft.client.particle.SmokeParticle) {
            if (++pym$debugCount % 20 == 1) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "[PYM-PARTICLE] {} emitter={} origin={} uniform={} prePos={} tracked={}",
                        particle.getClass().getSimpleName(), emitter,
                        origin == null ? null : origin.getUniqueId(), uniform, preKickPosition,
                        tracked == null ? null : tracked.getUniqueId());
            }
        }

        if (uniform <= 0.0D || Math.abs(uniform - 1.0D) <= ScaleBounds.EPSILON) return;

        particle.scale((float) uniform);

        // Sable already transforms particle velocity when it projects it out of the plot.
        // Terrain particles still need their gravity adjusted for the scaled craft.
        if (particle instanceof TerrainParticle) {
            motion.pym$setGravity((float) (motion.pym$getGravity() * uniform));
        }
    }

    @Unique
    private static double pym$uniformScale(ClientSubLevel subLevel) {
        double trackedScale = ScaleState.getClientScale(subLevel);
        if (Double.isFinite(trackedScale) && trackedScale > 0.0D
                && (ScaleState.hasClientSnapshot(subLevel.getUniqueId())
                || Math.abs(trackedScale - 1.0D) > ScaleBounds.EPSILON)) {
            return trackedScale;
        }

        Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - scale.y()) > ScaleBounds.EPSILON
                || Math.abs(scale.x() - scale.z()) > ScaleBounds.EPSILON) {
            return 0.0D;
        }

        double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D ? uniform : 0.0D;
    }
}
