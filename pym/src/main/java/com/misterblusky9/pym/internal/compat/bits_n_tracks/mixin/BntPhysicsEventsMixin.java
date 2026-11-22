package com.misterblusky9.pym.internal.compat.bits_n_tracks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.bits_n_tracks.UnitNormals;
import com.misterblusky9.pym.internal.physics.SolvedImpulseScale;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.qwxon.bitsntracks.physics.BntPhysicsEvents", remap = false)
public abstract class BntPhysicsEventsMixin {
    @WrapOperation(
            method = "applyAllBatchedForces",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"),
            remap = false
    )
    private static void pym$flushAsSolvedImpulse(
            RigidBodyHandle handle, ForceTotal forces, Operation<Void> original, @Local KineticBlockEntity wheel
    ) {
        if (!(Sable.HELPER.getContaining(wheel) instanceof ServerSubLevel subLevel)
                || !SolvedImpulseScale.enter(subLevel)) {
            original.call(handle, forces);
            return;
        }
        try {
            original.call(handle, forces);
        } finally {
            SolvedImpulseScale.exit();
        }
    }

    @WrapOperation(
            method = "resolveContact",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 1),
            remap = false
    )
    private static Vector3d pym$unitContactNormal(Pose3d pose, Vector3d normal, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, normal), normal);
    }

    @WrapOperation(
            method = "resolveContact",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 2),
            remap = false
    )
    private static Vector3d pym$unitContactUp(Pose3d pose, Vector3d up, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, up), up);
    }

    @WrapOperation(
            method = "fromBelt",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 0),
            remap = false
    )
    private static Vector3d pym$unitBeltUp(Pose3d pose, Vector3d up, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, up), up);
    }

    @WrapOperation(
            method = "drive",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3dc;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false
    )
    private static Vector3d pym$unitDriveUp(Pose3dc pose, Vector3d up, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, up), up);
    }

    @WrapOperation(
            method = "drive",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3dc;transformNormal(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false
    )
    private static Vector3d pym$unitDriveAxes(Pose3dc pose, Vector3d axis, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, axis), axis);
    }

    @WrapOperation(
            method = "castGround",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormal(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false
    )
    private static Vector3d pym$unitGroundNormal(Pose3d pose, Vector3d normal, Operation<Vector3d> original) {
        return UnitNormals.unit(original.call(pose, normal), normal);
    }
}
