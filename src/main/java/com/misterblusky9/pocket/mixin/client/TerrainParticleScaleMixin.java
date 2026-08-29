package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class TerrainParticleScaleMixin {
    @Inject(method = "add", at = @At("TAIL"))
    private void pocket$scaleSubLevelTerrainParticle(final Particle particle, final CallbackInfo ci) {
        if (!(particle instanceof TerrainParticle)) return;

        final ParticleExtension extension = (ParticleExtension) particle;
        extension.sable$initialKickOut();

        final SubLevel tracked = extension.sable$getTrackingSubLevel();
        if (!(tracked instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) return;

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) return;

        final double uniform = scale.x();
        if (!Double.isFinite(uniform) || uniform <= 0.0D
                || Math.abs(uniform - 1.0D) <= PocketSized.EPSILON) return;

        particle.scale((float) uniform);

        final double motionScale = uniform < 1.0D ? Math.sqrt(uniform) : uniform;
        final ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;
        motion.pocket$setXd(motion.pocket$getXd() * motionScale);
        motion.pocket$setYd(motion.pocket$getYd() * motionScale);
        motion.pocket$setZd(motion.pocket$getZd() * motionScale);
        motion.pocket$setGravity((float) (motion.pocket$getGravity() * motionScale));
    }
}
