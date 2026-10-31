package com.misterblusky9.pym.internal.compat.tracks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.InternalForces;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.qwxon.tracks.content.blocks.sable_track.SableTrackBlockEntity", remap = false)
public abstract class TrackForceScaleMixin {
    @ModifyExpressionValue(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            require = 0
    )
    private Vector3d pym$scaleSuspensionDamping(
            Vector3d velocity, ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep
    ) {
        double scale = Pym.scale().of(subLevel);
        if (ScaleBounds.isValid(scale)) velocity.y *= Math.sqrt(scale);
        return velocity;
    }

    @ModifyExpressionValue(
            method = "computeMaxExtensionToTerrain",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3dc;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            require = 0
    )
    private Vector3d pym$normalizeTerrainNormal(Vector3d normal) {
        return normal.lengthSquared() > 1.0E-12D ? normal.normalize() : normal;
    }

    @WrapOperation(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormal(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"),
            require = 0
    )
    private Vec3 pym$rotateGroundForce(Pose3d pose, Vec3 force, Operation<Vec3> original) {
        Vector3d rotated = pose.orientation().transform(new Vector3d(force.x, force.y, force.z));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    @WrapOperation(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"),
            require = 0
    )
    private Vec3 pym$rotateSuspensionForce(Pose3d pose, Vec3 force, Operation<Vec3> original) {
        Vector3d rotated = pose.orientation().transformInverse(new Vector3d(force.x, force.y, force.z));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    @WrapOperation(
            method = "applyBatchedForces",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"),
            require = 0
    )
    private void pym$flushTrackForces(
            RigidBodyHandle handle, ForceTotal forces, Operation<Void> original
    ) {
        if (!(Sable.HELPER.getContaining((BlockEntity) (Object) this) instanceof ServerSubLevel subLevel)
                || subLevel.isRemoved()) {
            original.call(handle, forces);
            return;
        }
        try (InternalForces ignored = Pym.internalForces(subLevel)) {
            original.call(handle, forces);
        }
    }
}
