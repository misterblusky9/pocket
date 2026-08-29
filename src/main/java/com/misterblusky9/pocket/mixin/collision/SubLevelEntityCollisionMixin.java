package com.misterblusky9.pocket.mixin.collision;

import com.misterblusky9.pocket.collision.SubLevelCollisionMetrics;
import com.misterblusky9.pocket.moon.MoonPhysicsCollision;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionMixin {
    @Inject(method = "collide", at = @At("HEAD"), remap = false)
    private static void pocket$beginCollisionMetrics(final CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir) {
        SubLevelCollisionMetrics.begin();
    }

    @Inject(method = "collide", at = @At("RETURN"), remap = false)
    private static void pocket$endCollisionMetrics(final CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir) {
        SubLevelCollisionMetrics.end();
    }

    @Inject(method = "collide", at = @At("RETURN"), remap = false)
    private static void pocket$collideMoon(
            final Entity entity,
            final Vec3 motion,
            final Vec3 velocity,
            final LevelReusedVectors sink,
            final CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir
    ) {
        MoonPhysicsCollision.apply(entity, cir.getReturnValue(), sink);
    }

    @Redirect(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"
            ),
            remap = false
    )
    private static Iterable<BlockPos> pocket$buildNonAirCandidates(
            final BlockPos min,
            final BlockPos max,
            @Local final LevelAccelerator accel
    ) {
        return SubLevelCollisionMetrics.candidates(accel, min, max);
    }

    @Redirect(
            method = {"collide", "hasCollision"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/util/LevelAccelerator;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            ),
            remap = false
    )
    private static BlockState pocket$reuseCandidateState(final LevelAccelerator accel, final BlockPos pos) {
        return SubLevelCollisionMetrics.state(accel, pos);
    }

    @WrapOperation(
            method = {"collide", "hasCollision"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/mixinterface/voxel_shape_iteration/FastVoxelShapeIterable;sable$allBoxes()Ljava/util/Iterator;"
            ),
            remap = false
    )
    private static Iterator<BoundingBox3dc> pocket$countVoxelBoxes(
            final FastVoxelShapeIterable shape,
            final Operation<Iterator<BoundingBox3dc>> original
    ) {
        return SubLevelCollisionMetrics.boxes(original.call(shape));
    }

    @WrapOperation(
            method = {"collide", "hasCollision"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;sat(Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private static Vector3d pocket$countSatCalls(
            final OrientedBoundingBox3d a,
            final OrientedBoundingBox3d b,
            final Vector3d dest,
            final Operation<Vector3d> original
    ) {
        SubLevelCollisionMetrics.sat();
        return original.call(a, b, dest);
    }

    @WrapOperation(
            method = "tryStepUp",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision;hasCollision(Ldev/ryanhcode/sable/util/LevelAccelerator;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;Ldev/ryanhcode/sable/companion/math/Pose3dc;Ljava/lang/Iterable;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3d;)Z"
            ),
            remap = false
    )
    private static boolean pocket$countStepProbes(
            final LevelAccelerator accel,
            final LevelReusedVectors sink,
            final Pose3dc subLevelPose,
            final Iterable<BlockPos> blocks,
            final OrientedBoundingBox3d entityBoundsOBB,
            final OrientedBoundingBox3d cubeOBB,
            final Vector3d boundsCenter,
            final Operation<Boolean> original
    ) {
        SubLevelCollisionMetrics.stepProbe();
        return original.call(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter);
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
    private static double pocket$breakEmptyMaxIter(
            final double value,
            @Local(name = "maxIter") final LocalIntRef maxIter
    ) {
        SubLevelCollisionMetrics.maxIter();
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
    private static Vector3d pocket$scaleSubLevelVoxelDimensions(
            final BoundingBox3dc box,
            final Vector3d dest,
            @Local(ordinal = 2) final Pose3d subLevelPose
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
    private static Vector3d pocket$scaleStepProbeVoxelDimensions(
            final BoundingBox3dc box,
            final Vector3d dest,
            @Local(argsOnly = true) final Pose3dc subLevelPose
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
    private static double pocket$scaleStepIncrement(
            final double value,
            @Local(argsOnly = true) final Pose3dc subLevelPose
    ) {
        return value * pocket$subLevelScale(subLevelPose);
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=0.1"),
            remap = false
    )
    private static double pocket$scaleStepInflation(
            final double value,
            @Local(argsOnly = true) final Pose3dc subLevelPose,
            @Local(argsOnly = true) final net.minecraft.world.entity.Entity entity,
            @Local(argsOnly = true) final net.minecraft.world.phys.AABB entityBounds
    ) {
        double scale = pocket$subLevelScale(subLevelPose);
        if (entity instanceof Player) {
            final double playerWidthScale = Math.min(entityBounds.getXsize(), entityBounds.getZsize()) / 0.6;
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
    private static double pocket$scaleStepProbeInset(
            final double value,
            @Local(argsOnly = true) final Pose3dc subLevelPose
    ) {
        return value * pocket$subLevelScale(subLevelPose);
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=-0.0625"),
            remap = false
    )
    private static double pocket$scaleAcceptedStepInset(
            final double value,
            @Local(argsOnly = true) final Pose3dc subLevelPose
    ) {
        return value * pocket$subLevelScale(subLevelPose);
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
    private static double pocket$scaleScaffoldingWorldSkew(
            final double coefficient,
            @Local(argsOnly = true) final Pose3dc subLevelPose
    ) {
        return coefficient - 0.05 * (1.0 - pocket$subLevelScale(subLevelPose));
    }

    @Unique
    private static double pocket$subLevelScale(final Pose3dc pose) {
        final double scale = Math.abs(pose.scale().x());
        return Double.isFinite(scale) && scale > 1.0E-7 ? scale : 1.0;
    }

}
