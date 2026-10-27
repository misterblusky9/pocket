package com.misterblusky9.pym.internal.mixin.collision;

import com.misterblusky9.pym.internal.collision.InteriorCollisionTolerance;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import com.misterblusky9.pym.internal.collision.SubLevelCollisionCache;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;



@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionMixin {
    /**
     * Sable 2.0.6 checks localBounds.volume() against 500^3 BEFORE it creates
     * the block candidate iterable.  The candidate redirection below clips
     * to the plot, but it cannot prevent that earlier guard from firing.
     *
     * Clipping this *local-space* search envelope before the guard preserves
     * collisions with blocks in the plot while excluding the enormous amount
     * of empty space created by inverse-transforming a world-sized entity
     * against a tiny or rapidly moving sub-level.
     *
     * Keep Sable's guard for genuinely oversized *in-plot* search envelopes:
     * never return a fake small volume when occupied space is actually large.
     */
    @WrapOperation(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3d;volume()D",
                    ordinal = 0
            ),
            remap = false,
            require = 1
    )
    private static double pym$clipLocalBoundsBeforeSableGuard(
            BoundingBox3d bounds,
            Operation<Double> original,
            @Local(name = "subLevel") SubLevel subLevel
    ) {
        // Bad pose coordinates are not a legitimate collision search.  Let
        // Sable's own guard reject them rather than passing NaN to BlockPos.
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY)
                || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)) {
            return Double.POSITIVE_INFINITY;
        }

        // Preserve Sable's original guard on the rare no-plot fallback path:
        // Pym's candidate builder deliberately leaves that scan unchanged.
        if (subLevel == null || subLevel.getPlot() == null) {
            return original.call(bounds);
        }

        // With a plot present, the later candidate builder returns no blocks
        // for empty plots or disjoint queries.
        if (!PlotScanBounds.clipCollisionSearchToPlot(subLevel, bounds)) {
            return 0.0D;
        }
        return original.call(bounds);
    }

    @Redirect(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"
            ),
            remap = false
    )
    private static Iterable<BlockPos> pym$buildCollisionCandidates(
            BlockPos min,
            BlockPos max,
            @Local(name = "subLevel") SubLevel subLevel,
            @Local LevelAccelerator accel
    ) {
        if (subLevel == null || subLevel.getPlot() == null) {
            return SubLevelCollisionCache.candidates(
                    accel,
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ()
            );
        }

        var plotBounds = subLevel.getPlot().getBoundingBox();
        if (plotBounds == null || plotBounds.volume() <= 0.0) {
            return java.util.List.of();
        }

        return SubLevelCollisionCache.candidates(
                accel,
                Math.max(min.getX(), plotBounds.minX()),
                Math.max(min.getY(), plotBounds.minY()),
                Math.max(min.getZ(), plotBounds.minZ()),
                Math.min(max.getX(), plotBounds.maxX()),
                Math.min(max.getY(), plotBounds.maxY()),
                Math.min(max.getZ(), plotBounds.maxZ())
        );
    }

    @Redirect(
            method = {"collide", "hasCollision"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/util/LevelAccelerator;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            ),
            remap = false
    )
    private static BlockState pym$reuseCollisionCandidateState(LevelAccelerator accel, BlockPos pos) {
        return SubLevelCollisionCache.state(accel, pos);
    }

    /**
     * Sable 2.0.6 uses an absolute 0.01 *local voxel-volume* tolerance when
     * deciding whether to discard a contact as an interior/shared-face hit.
     * For local shapes whose volume is less than 0.01, every possible difference
     * between their intersection and full volume is below that threshold, so
     * the test may incorrectly discard all of those contacts -- including
     * legitimate landing support from thin/complex shapes.
     *
     * Make it a one-percent *relative* tolerance instead. Ordinary full-cube
     * contacts retain Sable's 0.01 value and its control flow is unchanged.
     */
    @ModifyExpressionValue(
            method = "collide",
            at = @At(value = "CONSTANT", args = "doubleValue=0.01"),
            remap = false
    )
    private static double pym$relativeInteriorContactTolerance(
            double vanillaTolerance,
            @Local(name = "compressedMinAABB") BoundingBox3d compressedMinAABB
    ) {
        return InteriorCollisionTolerance.forShape(vanillaTolerance, compressedMinAABB.volume());
    }

    @ModifyExpressionValue(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Vector3d;lengthSquared()D",
                    ordinal = 2
            ),
            remap = false
    )
    private static double pym$breakEmptyMaxIter(
            double value,
            @Local(name = "maxIter") LocalIntRef maxIter
    ) {
        if (value <= 0.0) maxIter.set(4);
        return value;
    }

    @Redirect(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;size(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private static Vector3d pym$scaleSubLevelVoxelDimensions(
            BoundingBox3dc box,
            Vector3d dest,
            @Local(ordinal = 2) Pose3d subLevelPose
    ) {
        box.size(dest);
        dest.mul(subLevelPose.scale());
        return dest;
    }

    @Redirect(
            method = "hasCollision",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;size(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private static Vector3d pym$scaleStepProbeVoxelDimensions(
            BoundingBox3dc box,
            Vector3d dest,
            @Local(argsOnly = true) Pose3dc subLevelPose
    ) {
        box.size(dest);
        dest.mul(subLevelPose.scale());
        return dest;
    }
    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=0.0625"),
            remap = false
    )
    private static double pym$scaleStepIncrement(
            double value,
            @Local(argsOnly = true) Pose3dc subLevelPose
    ) {
        // Sable already uses entity.maxUpStep() in world units. Its probe stride must
        // follow the sublevel voxel size, including when the player is also small.
        return value * pym$subLevelScale(subLevelPose);
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=0.1"),
            remap = false
    )
    private static double pym$scaleStepInflation(
            double value,
            @Local(argsOnly = true) Pose3dc subLevelPose,
            @Local(argsOnly = true) net.minecraft.world.entity.Entity entity,
            @Local(argsOnly = true) net.minecraft.world.phys.AABB entityBounds
    ) {
        double scale = pym$subLevelScale(subLevelPose);
        if (entity instanceof Player) {
            double playerWidthScale = Math.min(entityBounds.getXsize(), entityBounds.getZsize()) / 0.6;
            if (Double.isFinite(playerWidthScale) && playerWidthScale > 0.0) {
                scale = Math.min(scale, playerWidthScale);
            }
        }
        return value * scale;
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=-0.125"),
            remap = false
    )
    private static double pym$scaleStepProbeInset(
            double value,
            @Local(argsOnly = true) Pose3dc subLevelPose
    ) {
        return value * pym$subLevelScale(subLevelPose);
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=-0.0625"),
            remap = false
    )
    private static double pym$scaleAcceptedStepInset(
            double value,
            @Local(argsOnly = true) Pose3dc subLevelPose
    ) {
        return value * pym$subLevelScale(subLevelPose);
    }

    @ModifyArg(
            method = "getSubLevelEntityCollisionShape",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Vector3dc;fma(DLorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            index = 0,
            remap = false
    )
    private static double pym$scaleScaffoldingWorldSkew(
            double coefficient,
            @Local(argsOnly = true) Pose3dc subLevelPose
    ) {
        return coefficient - 0.05 * (1.0 - pym$subLevelScale(subLevelPose));
    }

    @Unique
    private static double pym$subLevelScale(Pose3dc pose) {
        double scale = Math.abs(pose.scale().x());
        return Double.isFinite(scale) && scale > 1.0E-7 ? scale : 1.0;
    }

}
