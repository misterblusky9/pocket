package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleScaleMixin {
    @Unique private double pocket$scale = 1.0D;
    @Unique private int pocket$scaleStamp = Integer.MIN_VALUE;

    @Inject(method = "getQuadSize", at = @At("RETURN"), cancellable = true)
    private void pocket$scaleQuadSize(
            final float partialTick,
            final CallbackInfoReturnable<Float> cir
    ) {
        final double scale = pocket$currentScale();
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;
        cir.setReturnValue(cir.getReturnValueF() * (float) scale);
    }

    @Unique
    private double pocket$currentScale() {
        final ParticleMotionAccessor particle = (ParticleMotionAccessor) this;
        final int age = particle.pocket$getAge();
        if (this.pocket$scaleStamp == age) return this.pocket$scale;

        this.pocket$scaleStamp = age;
        this.pocket$scale = pocket$enclosingScale(particle);
        return this.pocket$scale;
    }

    @Unique
    private double pocket$enclosingScale(final ParticleMotionAccessor particle) {
        // Terrain particles already inherit their sub-level's scale on spawn.
        if ((Object) this instanceof TerrainParticle) return 1.0D;

        final ClientLevel level = particle.pocket$getLevel();
        if (level == null) return 1.0D;

        final Map<UUID, Double> scaled = ScaleState.clientScaledView();
        if (scaled.isEmpty()) return 1.0D;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 1.0D;

        final double px = particle.pocket$getX();
        final double py = particle.pocket$getY();
        final double pz = particle.pocket$getZ();

        double best = 1.0D;
        double bestVolume = Double.MAX_VALUE;

        for (final UUID id : scaled.keySet()) {
            final SubLevel raw = container.getSubLevel(id);
            if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final var bounds = subLevel.boundingBox();
            if (bounds == null) continue;
            if (px < bounds.minX() || px > bounds.maxX()
                    || py < bounds.minY() || py > bounds.maxY()
                    || pz < bounds.minZ() || pz > bounds.maxZ()) {
                continue;
            }

            final double volume = (bounds.maxX() - bounds.minX())
                    * (bounds.maxY() - bounds.minY())
                    * (bounds.maxZ() - bounds.minZ());
            if (volume >= bestVolume) continue;

            final double uniform = pocket$uniformScale(subLevel);
            if (uniform <= 0.0D) continue;

            bestVolume = volume;
            best = uniform;
        }

        return best;
    }

    @Unique
    private static double pocket$uniformScale(final ClientSubLevel subLevel) {
        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) {
            return 0.0D;
        }

        final double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D ? uniform : 0.0D;
    }
}
