package com.misterblusky9.pocket.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerScaleMixin {
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow private @Nullable Vector3d centerOfMass;
    @Shadow private @Nullable Vector3d lastCenterOfMass;

    @Unique
    private final Map<KinematicContraption, Vector3d> pocket$stableChildMassPositions = new WeakHashMap<>();

    @WrapOperation(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/sublevel/KinematicContraption;" +
                            "sable$getPosition(D)Lorg/joml/Vector3dc;"
            ),
            remap = false
    )
    private Vector3dc pocket$stabilizeScaledChildMassPosition(
            final KinematicContraption contraption,
            final double partialPhysicsTick,
            final Operation<Vector3dc> original
    ) {
        final double scale = ScaleState.getServerScale(this.subLevel);

        if (!Double.isFinite(scale)
                || scale <= 0.0D
                || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            this.pocket$stableChildMassPositions.remove(contraption);
            return original.call(contraption, partialPhysicsTick);
        }

        final Vector3d existing = this.pocket$stableChildMassPositions.get(contraption);
        if (existing != null) {
            return existing;
        }

        final Vector3dc livePosition = original.call(contraption, partialPhysicsTick);
        if (livePosition == null) {
            return null;
        }

        final Vector3d stablePosition = new Vector3d(livePosition);
        this.pocket$stableChildMassPositions.put(contraption, stablePosition);

        PocketTrace.scale(
                "anchored scaled contraption mass position uuid={} scale={} contraption={}@{} localMassPos={}",
                this.subLevel.getUniqueId(),
                scale,
                contraption.getClass().getSimpleName(),
                Integer.toHexString(System.identityHashCode(contraption)),
                stablePosition
        );

        return stablePosition;
    }

    @ModifyArg(
            method = "uploadData",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;teleport(" +
                            "Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;" +
                            "Lorg/joml/Vector3dc;" +
                            "Lorg/joml/Quaterniondc;)V"
            ),
            index = 1,
            remap = false
    )
    private Vector3dc pocket$scaleCenterOfMassRecenter(final Vector3dc sablePosition) {
        if (this.centerOfMass == null || this.lastCenterOfMass == null) {
            return sablePosition;
        }

        final double scale = ScaleState.getServerScale(this.subLevel);
        if (!Double.isFinite(scale)
                || scale <= 0.0D
                || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            return sablePosition;
        }

        final Vector3d rotatedDelta = new Vector3d(this.centerOfMass).sub(this.lastCenterOfMass);
        this.subLevel.logicalPose().orientation().transform(rotatedDelta);

        return new Vector3d(sablePosition).add(rotatedDelta.mul(scale - 1.0D));
    }
}
