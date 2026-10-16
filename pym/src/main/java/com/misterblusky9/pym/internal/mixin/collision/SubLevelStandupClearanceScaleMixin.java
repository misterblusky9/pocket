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

/**
 * CanFallAtleastHelper is used both for sublevel edge probes and by Sable's
 * player stand-up check. Sable 2.0.6 transforms each voxel center through the
 * sublevel pose but leaves the voxel OBB at a full block in world space.
 * It also shrinks player probe boxes by fixed world distances (0.1 / 0.05),
 * which can exceed the width of a Pehkui-scaled player.
 *
 * Keep the underlying clearance/collision algorithm (and other mods' hooks)
 * intact, changing only these world-space operands.
 */
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

    // Sable uses 0.1 total horizontal deflation on its OBB, and 0.05 per
    // side when computing the local block candidate region. Preserve both
    // values for an ordinary player; reduce them for miniature AABBs.
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
        // The standard player's horizontal width is 0.6; no Pehkui linkage
        // is needed because the actual collision box already includes its scale.
        double width = Math.min(aabb.getXsize(), aabb.getZsize());
        if (!Double.isFinite(width) || width <= 0.0) return 1.0;
        double ratio = width / 0.6;
        return ratio >= 0.99 ? 1.0 : Math.max(1.0E-6, ratio);
    }
}
