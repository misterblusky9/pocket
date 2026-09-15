package com.misterblusky9.pocket.mixin.collision;

import com.misterblusky9.pocket.collision.FastObbSat;
import com.misterblusky9.pocket.collision.SubLevelCollisionCache;
import com.misterblusky9.pocket.moon.MoonPhysicsCollision;
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
    private static Iterable<BlockPos> pocket$buildCollisionCandidates(
            final BlockPos min,
            final BlockPos max,
            @Local(name = "subLevel") final SubLevel subLevel,
            @Local final LevelAccelerator accel
    ) {
        if (subLevel == null || subLevel.getPlot() == null) {
            return SubLevelCollisionCache.candidates(
                    accel,
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ()
            );
        }

        final var plotBounds = subLevel.getPlot().getBoundingBox();
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
    private static BlockState pocket$reuseCollisionCandidateState(final LevelAccelerator accel, final BlockPos pos) {
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
    private static Vector3d pocket$fastCollisionSat(
            final OrientedBoundingBox3d a,
            final OrientedBoundingBox3d b,
            final Vector3d dest
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
    private static double pocket$breakEmptyMaxIter(
            final double value,
            @Local(name = "maxIter") final LocalIntRef maxIter
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
    @Inject(method = "tryStepUp", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pocket$fixTinySubLevelStepUp(
            final Entity entity,
            final LevelAccelerator accel,
            final LevelReusedVectors sink,
            final Pose3dc subLevelPose,
            final Iterable<BlockPos> blocks,
            final org.joml.Vector3dc entityBoundsCenter,
            final net.minecraft.world.phys.AABB entityBounds,
            final OrientedBoundingBox3d entityBoundsOBB,
            final OrientedBoundingBox3d cubeOBB,
            final org.joml.Vector3dc maxMTV,
            final org.joml.Vector3dc normalizedMTV,
            final Vector3d collisionMotion,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(entity instanceof Player)) {
            return;
        }

        final double playerWidthScale = Math.min(entityBounds.getXsize(), entityBounds.getZsize()) / 0.6;
        if (!Double.isFinite(playerWidthScale) || playerWidthScale <= 1.0E-7) {
            return;
        }

        final double subLevelScale = pocket$subLevelScale(subLevelPose);
        final double relativeScale = subLevelScale / playerWidthScale;
        if (relativeScale > 0.125 + 1.0E-7) {
            return;
        }

        cir.setReturnValue(pocket$tryTinyStepUp(
                entity, accel, sink, subLevelPose, blocks, entityBoundsCenter, entityBounds,
                entityBoundsOBB, cubeOBB, normalizedMTV, collisionMotion, subLevelScale, playerWidthScale
        ));
    }

    @Unique
    private static boolean pocket$tryTinyStepUp(
            final Entity entity,
            final LevelAccelerator accel,
            final LevelReusedVectors sink,
            final Pose3dc subLevelPose,
            final Iterable<BlockPos> blocks,
            final org.joml.Vector3dc entityBoundsCenter,
            final net.minecraft.world.phys.AABB entityBounds,
            final OrientedBoundingBox3d entityBoundsOBB,
            final OrientedBoundingBox3d cubeOBB,
            final org.joml.Vector3dc normalizedMTV,
            final Vector3d collisionMotion,
            final double subLevelScale,
            final double playerWidthScale
    ) {
        if (!entity.onGround()) {
            return false;
        }
        if (collisionMotion.dot(normalizedMTV) > 0.0) {
            return true;
        }

        final double maxStepHeight = entity.maxUpStep();
        if (!(maxStepHeight > 0.0)) {
            return false;
        }

        final double fineIncrement = (1.0 / 16.0) * subLevelScale;
        final double coarseIncrement = Math.min(1.0 / 16.0, fineIncrement * 8.0);
        if (!(fineIncrement > 1.0E-7) || !(coarseIncrement > 1.0E-7)) {
            return false;
        }

        final double inflationScale = Math.min(subLevelScale, playerWidthScale);
        final double inflation = 0.1 * inflationScale;
        final double probeInset = (2.0 / 16.0) * subLevelScale;
        final double acceptedInset = (1.0 / 16.0) * subLevelScale;

        entityBoundsOBB.getDimensions()
                .set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize())
                .add(inflation, inflation, inflation);

        final Vector3d boundsCenter = sink.stepHeightEntityBoundsCenter;
        final Vector3d probeMTV = sink.mtv;
        final Vector3d lastStepTestMTV = sink.lastStepTestMTV.zero();

        boundsCenter.set(entityBoundsCenter).fma(-probeInset, normalizedMTV);
        if (!pocket$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
            entityBoundsOBB.getDimensions().set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize());
            return false;
        }
        lastStepTestMTV.set(probeMTV);

        double lastCollidingHeight = 0.0;
        double firstFreeHeight = Double.NaN;

        for (double height = coarseIncrement; height <= maxStepHeight + 1.0E-9; height += coarseIncrement) {
            final double testedHeight = Math.min(height, maxStepHeight);
            boundsCenter.set(entityBoundsCenter)
                    .fma(testedHeight, sink.entityUpDirection)
                    .fma(-probeInset, normalizedMTV);

            if (pocket$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
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
            if (!pocket$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
                firstFreeHeight = maxStepHeight;
            } else {
                lastStepTestMTV.set(probeMTV);
            }
        }

        double resolvedStepHeight = Double.NaN;
        if (!Double.isNaN(firstFreeHeight)) {
            for (double height = lastCollidingHeight + fineIncrement; height <= firstFreeHeight + 1.0E-9; height += fineIncrement) {
                final double testedHeight = Math.min(height, firstFreeHeight);
                boundsCenter.set(entityBoundsCenter)
                        .fma(testedHeight, sink.entityUpDirection)
                        .fma(-probeInset, normalizedMTV);

                if (pocket$hasTinyStepCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
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
    private static boolean pocket$hasTinyStepCollision(
            final LevelAccelerator accel,
            final LevelReusedVectors sink,
            final Pose3dc subLevelPose,
            final Iterable<BlockPos> blocks,
            final OrientedBoundingBox3d entityBoundsOBB,
            final OrientedBoundingBox3d cubeOBB,
            final Vector3d boundsCenter
    ) {
        entityBoundsOBB.setPosition(boundsCenter);

        for (final BlockPos block : blocks) {
            final BlockState state = SubLevelCollisionCache.state(accel, block);
            if (state.isAir()) {
                continue;
            }

            final VoxelShape voxelShape = state.getCollisionShape(accel, block);
            final Iterator<BoundingBox3dc> iterator = ((FastVoxelShapeIterable) voxelShape).sable$allBoxes();
            while (iterator.hasNext()) {
                final BoundingBox3dc box = iterator.next();
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
    private static double pocket$scaleStepIncrement(
            final double value,
            @Local(argsOnly = true) final Pose3dc subLevelPose
    ) {
        return Math.max(value, value * pocket$subLevelScale(subLevelPose));
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
