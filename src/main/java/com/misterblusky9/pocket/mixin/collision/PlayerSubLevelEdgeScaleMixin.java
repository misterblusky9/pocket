package com.misterblusky9.pocket.mixin.collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerSubLevelEdgeScaleMixin extends LivingEntity {
    @Shadow @Final private Abilities abilities;

    @Shadow protected abstract boolean isStayingOnGroundSurface();

    @Shadow protected abstract boolean isAboveGround(float maxUpStep);

    protected PlayerSubLevelEdgeScaleMixin(final EntityType<? extends LivingEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true, order = 900)
    private void pocket$scaleSubLevelEdgeSearch(
            final Vec3 movement,
            final MoverType moverType,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this);
        if (trackingSubLevel == null) return;

        final float maxUpStep = this.maxUpStep();
        if (this.abilities.flying
                || movement.y > 0.0
                || (moverType != MoverType.SELF && moverType != MoverType.PLAYER)
                || !this.isStayingOnGroundSurface()
                || !this.isAboveGround(maxUpStep)) {
            return;
        }

        final Pose3dc pose = trackingSubLevel.lastPose();
        final double originalYaw = pose.orientation().getEulerAnglesYXZ(new Vector3d()).y;
        final Quaterniondc frameOrientation = new Quaterniond().rotateY(originalYaw);
        final Vector3dc localMovement = frameOrientation.transformInverse(new Vector3d(movement.x, 0.0, movement.z));
        double xMovement = localMovement.x();
        double zMovement = localMovement.z();

        final AABB bounds = this.getBoundingBox();
        final double subLevelScale = pocket$scale(pose.scale().x());
        final double playerWidthScale = pocket$scale(Math.min(bounds.getXsize(), bounds.getZsize()) / 0.6);
        final double step = 0.05 * Math.min(subLevelScale, playerWidthScale);
        final double signedXStep = Math.signum(xMovement) * step;
        final double signedZStep = Math.signum(zMovement) * step;

        while (xMovement != 0.0 && pocket$wouldSlideOff(xMovement, 0.0, maxUpStep, frameOrientation)) {
            if (Math.abs(xMovement) <= step) {
                xMovement = 0.0;
                break;
            }
            xMovement -= signedXStep;
        }

        while (zMovement != 0.0 && pocket$wouldSlideOff(0.0, zMovement, maxUpStep, frameOrientation)) {
            if (Math.abs(zMovement) <= step) {
                zMovement = 0.0;
                break;
            }
            zMovement -= signedZStep;
        }

        while (xMovement != 0.0 && zMovement != 0.0 && pocket$wouldSlideOff(xMovement, zMovement, maxUpStep, frameOrientation)) {
            if (Math.abs(xMovement) <= step) {
                xMovement = 0.0;
            } else {
                xMovement -= signedXStep;
            }

            if (Math.abs(zMovement) <= step) {
                zMovement = 0.0;
            } else {
                zMovement -= signedZStep;
            }
        }

        final Vector3d globalMovement = frameOrientation.transform(new Vector3d(xMovement, 0.0, zMovement));
        cir.setReturnValue(new Vec3(globalMovement.x, movement.y, globalMovement.z));
    }

    @Unique
    private boolean pocket$wouldSlideOff(
            final double localXMovement,
            final double localZMovement,
            final float fallDistance,
            final Quaterniondc frameOrientation
    ) {
        final Vector3d movement = new Vector3d(localXMovement, 0.0, localZMovement);
        frameOrientation.transform(movement);
        final AABB bounds = this.getBoundingBox();
        final AABB boundsToCheck = new AABB(
                bounds.minX + movement.x,
                bounds.minY - (double) fallDistance - 1.0E-5F,
                bounds.minZ + movement.z,
                bounds.maxX + movement.x,
                bounds.minY,
                bounds.maxZ + movement.z
        );
        return CanFallAtleastHelper.canFallAtleastWithSubLevels(this.level(), boundsToCheck) == null;
    }

    @Unique
    private static double pocket$scale(final double value) {
        final double scale = Math.abs(value);
        return Double.isFinite(scale) && scale > 1.0E-7 ? scale : 1.0;
    }
}
