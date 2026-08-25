package com.misterblusky9.pocket.mixin.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerScaleMixin {
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow private @Nullable Vector3d centerOfMass;
    @Shadow private @Nullable Vector3d lastCenterOfMass;

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
