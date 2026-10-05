package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
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

import java.util.Map;
import java.util.UUID;

@Mixin(ParticleEngine.class)
public abstract class ParticleSpawnScaleMixin {
    @Inject(method = "add", at = @At("TAIL"))
    private void pym$inheritSubLevelScale(final Particle particle, final CallbackInfo ci) {
        final ParticleMotionAccessor motion = (ParticleMotionAccessor) particle;
        final ClientLevel level = motion.pym$getLevel();
        if (level == null) return;

        final Vec3 preKickPosition = new Vec3(
                motion.pym$getX(),
                motion.pym$getY(),
                motion.pym$getZ()
        );
        final ClientSubLevel preKickOrigin = Sable.HELPER.getContainingClient(preKickPosition);

        final ParticleExtension extension = (ParticleExtension) particle;
        extension.sable$initialKickOut();

        ClientSubLevel origin = null;
        final SubLevel tracked = extension.sable$getTrackingSubLevel();
        if (tracked instanceof final ClientSubLevel trackedClient && !trackedClient.isRemoved()) {
            origin = trackedClient;
        } else if (preKickOrigin != null && !preKickOrigin.isRemoved()) {
            origin = preKickOrigin;
        }

        final double uniform;
        if (origin != null) {
            uniform = pym$uniformScale(origin);
        } else {
            uniform = pym$enclosingScale(
                    level,
                    motion.pym$getX(),
                    motion.pym$getY(),
                    motion.pym$getZ()
            );
        }

        if (uniform <= 0.0D || Math.abs(uniform - 1.0D) <= ScaleBounds.EPSILON) return;

        particle.scale((float) uniform);

        if (!(particle instanceof TerrainParticle)) return;

        final double motionScale = uniform < 1.0D ? Math.sqrt(uniform) : uniform;
        motion.pym$setXd(motion.pym$getXd() * motionScale);
        motion.pym$setYd(motion.pym$getYd() * motionScale);
        motion.pym$setZd(motion.pym$getZd() * motionScale);
        motion.pym$setGravity((float) (motion.pym$getGravity() * motionScale));
    }

    @Unique
    private static double pym$enclosingScale(
            final ClientLevel level,
            final double x,
            final double y,
            final double z
    ) {
        final Map<UUID, Double> scaled = ScaleState.clientScaledView();
        if (scaled.isEmpty()) return 1.0D;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 1.0D;

        double bestScale = 1.0D;
        double bestVolume = Double.MAX_VALUE;

        for (final UUID id : scaled.keySet()) {
            final SubLevel raw = container.getSubLevel(id);
            if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final var bounds = subLevel.boundingBox();
            if (bounds == null
                    || x < bounds.minX() || x > bounds.maxX()
                    || y < bounds.minY() || y > bounds.maxY()
                    || z < bounds.minZ() || z > bounds.maxZ()) {
                continue;
            }

            final double volume = (bounds.maxX() - bounds.minX())
                    * (bounds.maxY() - bounds.minY())
                    * (bounds.maxZ() - bounds.minZ());
            if (volume >= bestVolume) continue;

            final double uniform = pym$uniformScale(subLevel);
            if (uniform <= 0.0D) continue;

            bestVolume = volume;
            bestScale = uniform;
        }

        return bestScale;
    }

    @Unique
    private static double pym$uniformScale(final ClientSubLevel subLevel) {
        final double trackedScale = ScaleState.getClientScale(subLevel);
        if (Double.isFinite(trackedScale) && trackedScale > 0.0D
                && (ScaleState.hasClientSnapshot(subLevel.getUniqueId())
                || Math.abs(trackedScale - 1.0D) > ScaleBounds.EPSILON)) {
            return trackedScale;
        }

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - scale.y()) > ScaleBounds.EPSILON
                || Math.abs(scale.x() - scale.z()) > ScaleBounds.EPSILON) {
            return 0.0D;
        }

        final double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D ? uniform : 0.0D;
    }
}
