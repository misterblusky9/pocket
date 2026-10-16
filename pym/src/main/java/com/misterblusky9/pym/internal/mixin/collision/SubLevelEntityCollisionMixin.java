package com.misterblusky9.pym.internal.mixin.collision;

import com.misterblusky9.pym.internal.collision.FastObbSat;
import com.misterblusky9.pym.internal.collision.SubLevelCollisionCache;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    @Redirect(
            method = {"collide", "hasCollision"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;sat(Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private static Vector3d pym$fastCollisionSat(
            OrientedBoundingBox3d a,
            OrientedBoundingBox3d b,
            Vector3d dest
    ) {
        return FastObbSat.sat(a, b, dest);
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
    @Inject(method = "tryStepUp", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pym$fixTinySubLevelStepUp(
            Entity entity,
            LevelAccelerator accel,
            LevelReusedVectors sink,
            Pose3dc subLevelPose,
            Iterable<BlockPos> blocks,
            org.joml.Vector3dc entityBoundsCenter,
            net.minecraft.world.phys.AABB entityBounds,
            OrientedBoundingBox3d entityBoundsOBB,
            OrientedBoundingBox3d cubeOBB,
            org.joml.Vector3dc maxMTV,
            org.joml.Vector3dc normalizedMTV,
            Vector3d collisionMotion,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(entity instanceof Player)) {
            return;
        }

        double playerWidthScale = Math.min(entityBounds.getXsize(), entityBounds.getZsize()) / 0.6;
        if (!Double.isFinite(playerWidthScale) || playerWidthScale <= 1.0E-7) {
            return;
        }

        double subLevelScale = pym$subLevelScale(subLevelPose);
        double relativeScale = subLevelScale / playerWidthScale;
        if (relativeScale > 0.125 + 1.0E-7) {
            return;
        }

        cir.setReturnValue(pym$tryTinyStepUp(
                entity, accel, sink, subLevelPose, blocks, entityBoundsCenter, entityBounds,
                entityBoundsOBB, cubeOBB, normalizedMTV, collisionMotion, subLevelScale, playerWidthScale
        ));
    }

    @Unique
    private static boolean pym$tryTinyStepUp(
            Entity entity,
            LevelAccelerator accel,
            LevelReusedVectors sink,
            Pose3dc subLevelPose,
            Iterable<BlockPos> blocks,
            org.joml.Vector3dc entityBoundsCenter,
            net.minecraft.world.phys.AABB entityBounds,
            OrientedBoundingBox3d entityBoundsOBB,
            OrientedBoundingBox3d cubeOBB,
            org.joml.Vector3dc normalizedMTV,
            Vector3d collisionMotion,
            double subLevelScale,
            double playerWidthScale
    ) {
        if (!entity.onGround()) {
            return false;
        }
        if (collisionMotion.dot(normalizedMTV) > 0.0) {
            return true;
        }

        double maxStepHeight = entity.maxUpStep();
        if (!(maxStepHeight > 0.0)) {
            return false;
        }

        double fineIncrement = (1.0 / 16.0) * subLevelScale;
        double coarseIncrement = Math.min(1.0 / 16.0, fineIncrement * 8.0);
        if (!(fineIncrement > 1.0E-7) || !(coarseIncrement > 1.0E-7)) {
            return false;
        }

        double inflationScale = Math.min(subLevelScale, playerWidthScale);
        double inflation = 0.1 * inflationScale;
        double probeInset = (2.0 / 16.0) * subLevelScale;
        double acceptedInset = (1.0 / 16.0) * subLevelScale;

        entityBoundsOBB.getDimensions()
                .set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize())
                .add(inflation, inflation, inflation);

        Vector3d boundsCenter = sink.stepHeightEntityBoundsCenter;
        Vector3d probeMTV = sink.mtv;
        Vector3d lastStepTestMTV = sink.lastStepTestMTV.zero();

        boundsCenter.set(entityBoundsCenter).fma(-probeInset, normalizedMTV);
        if (!pym$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
            entityBoundsOBB.getDimensions().set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize());
            return false;
        }
        lastStepTestMTV.set(probeMTV);

        double lastCollidingHeight = 0.0;
        double firstFreeHeight = Double.NaN;

        for (double height = coarseIncrement; height <= maxStepHeight + 1.0E-9; height += coarseIncrement) {
            double testedHeight = Math.min(height, maxStepHeight);
            boundsCenter.set(entityBoundsCenter)
                    .fma(testedHeight, sink.entityUpDirection)
                    .fma(-probeInset, normalizedMTV);

            if (pym$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
                lastCollidingHeight = testedHeight;
                lastStepTestMTV.set(probeMTV);
            } else {
                firstFreeHeight = testedHeight;
                break;
            }

            if (testedHeight >= maxStepHeight - 1.0E-9) {
                break;
            }
        }

        if (Double.isNaN(firstFreeHeight) && lastCollidingHeight < maxStepHeight - 1.0E-9) {
            boundsCenter.set(entityBoundsCenter)
                    .fma(maxStepHeight, sink.entityUpDirection)
                    .fma(-probeInset, normalizedMTV);
            if (!pym$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
                firstFreeHeight = maxStepHeight;
            } else {
                lastStepTestMTV.set(probeMTV);
            }
        }

        double resolvedStepHeight = Double.NaN;
        if (!Double.isNaN(firstFreeHeight)) {
            for (double height = lastCollidingHeight + fineIncrement; height <= firstFreeHeight + 1.0E-9; height += fineIncrement) {
                double testedHeight = Math.min(height, firstFreeHeight);
                boundsCenter.set(entityBoundsCenter)
                        .fma(testedHeight, sink.entityUpDirection)
                        .fma(-probeInset, normalizedMTV);

                if (pym$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
                    lastStepTestMTV.set(probeMTV);
                } else {
                    resolvedStepHeight = testedHeight;
                    break;
                }

                if (testedHeight >= firstFreeHeight - 1.0E-9) {
                    break;
                }
            }
        }

        entityBoundsOBB.getDimensions().set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize());

        if (Double.isNaN(resolvedStepHeight) || lastStepTestMTV.lengthSquared() <= 0.0) {
            return false;
        }
        if (lastStepTestMTV.normalize().dot(sink.entityUpDirection) <= 0.8) {
            return false;
        }

        collisionMotion
                .fma(resolvedStepHeight, sink.entityUpDirection)
                .fma(-acceptedInset, normalizedMTV);
        return true;
    }

    @Unique
    private static boolean pym$hasTinyStepCollision(
            LevelAccelerator accel,
            LevelReusedVectors sink,
            Pose3dc subLevelPose,
            Iterable<BlockPos> blocks,
            OrientedBoundingBox3d entityBoundsOBB,
            OrientedBoundingBox3d cubeOBB,
            Vector3d boundsCenter
    ) {
        entityBoundsOBB.setPosition(boundsCenter);

        for (BlockPos block : blocks) {
            BlockState state = SubLevelCollisionCache.state(accel, block);
            if (state.isAir()) {
                continue;
            }

            VoxelShape voxelShape = state.getCollisionShape(accel, block);
            Iterator<BoundingBox3dc> iterator = ((FastVoxelShapeIterable) voxelShape).sable$allBoxes();
            while (iterator.hasNext()) {
                BoundingBox3dc box = iterator.next();
                box.center(sink.center);
                cubeOBB.getPosition().set(
                        block.getX() + sink.center.x,
                        block.getY() + sink.center.y,
                        block.getZ() + sink.center.z
                );
                subLevelPose.transformPosition(cubeOBB.getPosition());
                box.size(cubeOBB.getDimensions());
                cubeOBB.getDimensions().mul(subLevelPose.scale());

                FastObbSat.sat(entityBoundsOBB, cubeOBB, sink.mtv);
                if (sink.mtv.lengthSquared() > 0.0
                        && sink.mtv.x != Double.MAX_VALUE
                        && sink.mtv.y != Double.MAX_VALUE
                        && sink.mtv.z != Double.MAX_VALUE) {
                    return true;
                }
            }
        }

        return false;
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
        return Math.max(value, value * pym$subLevelScale(subLevelPose));
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
