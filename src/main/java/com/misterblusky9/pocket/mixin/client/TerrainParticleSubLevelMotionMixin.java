package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Particle.class, priority = 900)
public abstract class TerrainParticleSubLevelMotionMixin {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected double xd;
    @Shadow protected double yd;
    @Shadow protected double zd;
    @Shadow protected boolean onGround;

    @Unique private boolean pocket$motionCaptureActive;
    @Unique private double pocket$beforeX;
    @Unique private double pocket$beforeY;
    @Unique private double pocket$beforeZ;
    @Unique private double pocket$beforeXd;
    @Unique private double pocket$beforeYd;
    @Unique private double pocket$beforeZd;
    @Unique private double pocket$requestedX;
    @Unique private double pocket$requestedY;
    @Unique private double pocket$requestedZ;
    @Unique private double pocket$motionScale;

    @Inject(method = "move", at = @At("HEAD"))
    private void pocket$captureScaledTerrainMotion(final double xMotion, final double yMotion, final double zMotion, final CallbackInfo ci) {
        final Particle particle = (Particle) (Object) this;
        if (!(particle instanceof TerrainParticle)) {
            this.pocket$motionCaptureActive = false;
            return;
        }

        final SubLevel tracked = ((ParticleExtension) particle).sable$getTrackingSubLevel();
        if (!(tracked instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
            this.pocket$motionCaptureActive = false;
            return;
        }

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) {
            this.pocket$motionCaptureActive = false;
            return;
        }

        final double uniform = scale.x();
        if (!Double.isFinite(uniform) || uniform <= 0.0D || uniform >= 1.0D - PocketSized.EPSILON) {
            this.pocket$motionCaptureActive = false;
            return;
        }

        this.pocket$motionCaptureActive = true;
        this.pocket$beforeX = this.x;
        this.pocket$beforeY = this.y;
        this.pocket$beforeZ = this.z;
        this.pocket$beforeXd = this.xd;
        this.pocket$beforeYd = this.yd;
        this.pocket$beforeZd = this.zd;
        this.pocket$requestedX = xMotion;
        this.pocket$requestedY = yMotion;
        this.pocket$requestedZ = zMotion;
        this.pocket$motionScale = uniform;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void pocket$restoreUnblockedTerrainMotion(final double xMotion, final double yMotion, final double zMotion, final CallbackInfo ci) {
        if (!this.pocket$motionCaptureActive) return;
        this.pocket$motionCaptureActive = false;

        final double movedX = this.x - this.pocket$beforeX;
        final double movedY = this.y - this.pocket$beforeY;
        final double movedZ = this.z - this.pocket$beforeZ;
        final double epsilon = Math.max(1.0E-8D, this.pocket$motionScale * 1.0E-6D);

        final boolean xPassed = pocket$axisPassed(this.pocket$requestedX, movedX, epsilon);
        final boolean yPassed = pocket$axisPassed(this.pocket$requestedY, movedY, epsilon);
        final boolean zPassed = pocket$axisPassed(this.pocket$requestedZ, movedZ, epsilon);

        if (xPassed && Math.abs(this.pocket$beforeXd) > epsilon && Math.abs(this.xd) < Math.abs(this.pocket$beforeXd) * 0.5D) {
            this.xd = this.pocket$beforeXd;
        }
        if (yPassed && Math.abs(this.pocket$beforeYd) > epsilon && Math.abs(this.yd) < Math.abs(this.pocket$beforeYd) * 0.5D) {
            this.yd = this.pocket$beforeYd;
        }
        if (zPassed && Math.abs(this.pocket$beforeZd) > epsilon && Math.abs(this.zd) < Math.abs(this.pocket$beforeZd) * 0.5D) {
            this.zd = this.pocket$beforeZd;
        }

        if (yPassed && Math.abs(this.pocket$requestedY) > epsilon) {
            this.onGround = false;
            ((ParticleMotionAccessor) this).pocket$setStoppedByCollision(false);
        }
    }

    @Unique
    private static boolean pocket$axisPassed(final double requested, final double moved, final double epsilon) {
        final double amount = Math.abs(requested);
        if (amount <= epsilon) return false;
        if (requested * moved <= 0.0D) return false;
        return Math.abs(moved) + epsilon >= amount * 0.7D;
    }
}
