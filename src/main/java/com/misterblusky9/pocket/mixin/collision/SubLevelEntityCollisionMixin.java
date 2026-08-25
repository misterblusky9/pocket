package com.misterblusky9.pocket.mixin.collision;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionMixin {
    @Redirect(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;size(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private static Vector3d pocket$scaleSubLevelVoxelDimensions(
            final BoundingBox3dc box,
            final Vector3d dest,
            @Local(ordinal = 2) final Pose3d subLevelPose
    ) {
        box.size(dest);
        dest.mul(subLevelPose.scale());
        return dest;
    }
}
