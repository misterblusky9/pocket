package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.maxenonyme.highseas.client.SailShaderState", remap = false)
public abstract class SailShaderOffsetScaleMixin {
    @WrapOperation(
            method = "prepareForSublevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaterniondc;transformInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 0
            ),
            remap = false
    )
    private static Vector3d pym$offsetInPlotUnits(
            Quaterniondc rotation,
            Vector3d offset,
            Operation<Vector3d> original,
            @Local(argsOnly = true) Pose3dc renderPose
    ) {
        Vector3d local = original.call(rotation, offset);
        Vector3dc scale = renderPose.scale();
        return local.div(scale.x(), scale.y(), scale.z());
    }
}
