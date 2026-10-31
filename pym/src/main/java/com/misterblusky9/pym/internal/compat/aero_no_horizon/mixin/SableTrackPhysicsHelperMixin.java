package com.misterblusky9.pym.internal.compat.aero_no_horizon.mixin;

import com.misterblusky9.pym.internal.compat.aero_no_horizon.ScaledContactSample;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.aero_no_horizon.trackwork.content.track.SableTrackPhysicsHelper", remap = false)
public abstract class SableTrackPhysicsHelperMixin {
    @ModifyArg(
            method = {"accumulateTrackForces", "isNodeGrounded", "sampleWheelTravel"},
            at = @At(value = "INVOKE", target = "Lcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper;raycastGround(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;DILcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper$ContactAggregationMode;)Lcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper$ContactSample;"),
            index = 4,
            remap = false,
            require = 0
    )
    private static double pym$scaleRayLength(double length, @Local(argsOnly = true) ServerSubLevel subLevel) {
        return length * pym$scale(subLevel);
    }

    @ModifyArg(
            method = {"accumulateTrackForces", "isNodeGrounded", "sampleWheelTravel"},
            at = @At(value = "INVOKE", target = "Lcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper;raycastGround(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;DILcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper$ContactAggregationMode;)Lcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper$ContactSample;"),
            index = 6,
            remap = false,
            require = 0
    )
    private static double pym$scaleContactWidth(double width, @Local(argsOnly = true) ServerSubLevel subLevel) {
        return width * pym$scale(subLevel);
    }

    @Inject(method = "raycastGround", at = @At("RETURN"), remap = false, require = 0)
    private static void pym$convertHitDistance(
            CallbackInfoReturnable<?> cir,
            @Local(argsOnly = true) ServerSubLevel subLevel
    ) {
        Object contact = cir.getReturnValue();
        if (contact instanceof ScaledContactSample sample) {
            sample.pym$setScale(pym$scale(subLevel));
        }
    }

    @ModifyArg(
            method = "accumulateTrackForces",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;mul(DLorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 0),
            index = 0,
            remap = false,
            require = 0
    )
    private static double pym$trackSpeed(double speed, @Local(argsOnly = true) ServerSubLevel subLevel) {
        return speed * pym$scale(subLevel);
    }

    @ModifyArg(
            method = "accumulateTrackForces",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;mul(DLorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 1),
            index = 0,
            remap = false,
            require = 0
    )
    private static double pym$wheelSpeed(double speed, @Local(argsOnly = true) ServerSubLevel subLevel) {
        return speed * pym$scale(subLevel);
    }

    @ModifyExpressionValue(
            method = "accumulateTrackworkSuspensionForce",
            at = @At(value = "INVOKE", target = "Lcom/github/aero_no_horizon/trackwork/content/track/SableTrackRuntimeCache;updateDampingFilter(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/core/BlockPos;DDD)D"),
            remap = false,
            require = 0
    )
    private static double pym$scaleDampingSpeed(double speed, @Local(argsOnly = true) ServerSubLevel subLevel) {
        return speed * Math.sqrt(pym$scale(subLevel));
    }

    @ModifyArg(
            method = "accumulateTrackForces",
            at = @At(value = "INVOKE", target = "Lcom/github/aero_no_horizon/trackwork/content/track/SableTrackPhysicsHelper;queuePointImpulse(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/force/ForceGroup;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"),
            index = 3,
            remap = false,
            require = 2
    )
    private static Vector3dc pym$scaleQueuedImpulse(
            Vector3dc impulse,
            @Local(argsOnly = true) ServerSubLevel subLevel
    ) {
        double scale = pym$scale(subLevel);
        return scale == 1.0D ? impulse : new Vector3d(impulse).mul(scale);
    }

    private static double pym$scale(ServerSubLevel subLevel) {
        double scale = Pym.scale().of(subLevel);
        return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }
}
