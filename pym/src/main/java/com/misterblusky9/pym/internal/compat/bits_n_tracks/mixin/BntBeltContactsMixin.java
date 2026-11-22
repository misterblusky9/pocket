package com.misterblusky9.pym.internal.compat.bits_n_tracks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.bits_n_tracks.UnitNormals;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.qwxon.bitsntracks.physics.BntBeltContacts", remap = false)
public abstract class BntBeltContactsMixin {
    @WrapOperation(
            method = "sample",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false
    )
    private static Vector3d pym$unitBeltNormal(Pose3d pose, Vector3d normal, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, normal), normal);
    }
}
