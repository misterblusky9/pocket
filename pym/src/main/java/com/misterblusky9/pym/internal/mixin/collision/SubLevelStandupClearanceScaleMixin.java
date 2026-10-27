package com.misterblusky9.pym.internal.mixin.collision;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CanFallAtleastHelper.class, remap = false)
public abstract class SubLevelStandupClearanceScaleMixin {
    @ModifyExpressionValue(
            method = "canFallAtleastWithSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;size(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private static Vector3d pym$scaleVoxelBounds(Vector3d dimensions, @Local(name = "pose") Pose3dc pose) {
        return dimensions.mul(pose.scale());
    }

    @ModifyExpressionValue(
            method = "canFallAtleastWithSubLevels",
            at = @At(value = "CONSTANT", args = "doubleValue=0.1"),
            remap = false
    )
    private static double pym$scaleProbeWidthInset(double value, @Local(argsOnly = true) AABB aabb) {
        return value * pym$probeWidthScale(aabb);
    }

    @ModifyExpressionValue(
            method = "canFallAtleastWithSubLevels",
            at = @At(value = "CONSTANT", args = "doubleValue=-0.05"),
            remap = false
    )
    private static double pym$scaleSearchInset(double value, @Local(argsOnly = true) AABB aabb) {
        return value * pym$probeWidthScale(aabb);
    }

    @Unique
    private static double pym$probeWidthScale(AABB aabb) {
        double width = Math.min(aabb.getXsize(), aabb.getZsize());
        if (!Double.isFinite(width) || width <= 0.0) return 1.0;
        double ratio = width / 0.6;
        return ratio >= 0.99 ? 1.0 : Math.max(1.0E-6, ratio);
    }
}
