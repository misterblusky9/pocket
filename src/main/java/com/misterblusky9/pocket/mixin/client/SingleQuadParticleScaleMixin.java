package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleScaleMixin {
    @Unique private double pocket$bornScale;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V", at = @At("TAIL"))
    private void pocket$captureBornScale(
            final ClientLevel level, final double x, final double y, final double z, final CallbackInfo ci
    ) {
        this.pocket$bornScale = pocket$scaleAtBirth(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V", at = @At("TAIL"))
    private void pocket$captureBornScaleWithVelocity(
            final ClientLevel level,
            final double x, final double y, final double z,
            final double xSpeed, final double ySpeed, final double zSpeed,
            final CallbackInfo ci
    ) {
        this.pocket$bornScale = pocket$scaleAtBirth(level, x, y, z);
    }

    @Unique
    private static boolean pocket$sizeScaledByCaller;

    @Redirect(
            method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/SingleQuadParticle;getQuadSize(F)F"
            )
    )
    private float pocket$scaleParticleQuad(final SingleQuadParticle particle, final float partialTick) {
        final float vanillaSize;
        pocket$sizeScaledByCaller = true;
        try {
            vanillaSize = particle.getQuadSize(partialTick);
        } finally {
            pocket$sizeScaledByCaller = false;
        }

        final double scale = this.pocket$bornScale;
        if (scale <= 0.0D || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return vanillaSize;

        return vanillaSize * (float) scale;
    }

    @Inject(method = "getQuadSize", at = @At("RETURN"), cancellable = true)
    private void pocket$scaleQuadSizeAtSource(
            final float partialTick, final CallbackInfoReturnable<Float> cir
    ) {
        if (pocket$sizeScaledByCaller) return;

        final double scale = this.pocket$bornScale;
        if (scale <= 0.0D || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        cir.setReturnValue(cir.getReturnValueF() * (float) scale);
    }

    @Unique
    private static double pocket$scaleAtBirth(
            final ClientLevel level, final double x, final double y, final double z
    ) {
        if (level == null) return 0.0D;

        final SubLevel plotOwner = Sable.HELPER.getContainingClient((Position) new Vec3(x, y, z));
        if (plotOwner instanceof final ClientSubLevel subLevel && !subLevel.isRemoved()) {
            return pocket$uniformScale(subLevel);
        }

        final Map<UUID, Double> scaled = ScaleState.clientScaledView();
        if (scaled.isEmpty()) return 0.0D;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 0.0D;

        double best = 0.0D;
        double bestVolume = Double.MAX_VALUE;

        for (final UUID id : scaled.keySet()) {
            final SubLevel raw = container.getSubLevel(id);
            if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final var bounds = subLevel.boundingBox();
            if (bounds == null) continue;
            if (x < bounds.minX() || x > bounds.maxX()
                    || y < bounds.minY() || y > bounds.maxY()
                    || z < bounds.minZ() || z > bounds.maxZ()) {
                continue;
            }

            final double volume = (bounds.maxX() - bounds.minX())
                    * (bounds.maxY() - bounds.minY())
                    * (bounds.maxZ() - bounds.minZ());
            if (volume >= bestVolume) continue;

            bestVolume = volume;
            best = pocket$uniformScale(subLevel);
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
