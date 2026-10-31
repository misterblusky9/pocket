package com.misterblusky9.pym.internal.mixin.collision;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;
import com.misterblusky9.pym.internal.collision.CollisionLog;
import com.misterblusky9.pym.internal.collision.CollisionTrace;
import com.misterblusky9.pym.internal.collision.InteriorCollisionTolerance;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import com.misterblusky9.pym.internal.collision.SubLevelCollisionCache;
import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionMixin {
    @Shadow(remap = false)
    private static boolean hasCollision(
            LevelAccelerator accel, LevelReusedVectors sink, Pose3dc pose,
            Iterable<BlockPos> blocks, OrientedBoundingBox3d entityOBB,
            OrientedBoundingBox3d cubeOBB, Vector3d center
    ) {
        throw new AssertionError();
    }

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
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY)
                || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)) {
            return Double.POSITIVE_INFINITY;
        }

        if (subLevel == null || subLevel.getPlot() == null) {
            return original.call(bounds);
        }

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
            @Local LevelAccelerator accel,
            @Local(name = "subLevelPose") Pose3d pose,
            @Local(argsOnly = true) Entity entity
    ) {
        CollisionTrace.begin(entity, subLevel);
        SubLevelIndexedCollision.reset();
        SubLevelCollisionCache.clear();
        if (subLevel == null || subLevel.getPlot() == null) {
            CollisionLog.original();
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

        int minX = Math.max(min.getX(), plotBounds.minX());
        int minY = Math.max(min.getY(), plotBounds.minY());
        int minZ = Math.max(min.getZ(), plotBounds.minZ());
        int maxX = Math.min(max.getX(), plotBounds.maxX());
        int maxY = Math.min(max.getY(), plotBounds.maxY());
        int maxZ = Math.min(max.getZ(), plotBounds.maxZ());
        Iterable<BlockPos> indexed = SubLevelIndexedCollision.candidates(
                subLevel, pym$subLevelScale(pose), entity, minX, minY, minZ, maxX, maxY, maxZ);
        if (indexed == null) CollisionLog.original();
        return indexed != null ? indexed : SubLevelCollisionCache.candidates(
                accel, minX, minY, minZ, maxX, maxY, maxZ);
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
        return SubLevelIndexedCollision.active() ? SubLevelIndexedCollision.state(accel, pos) : SubLevelCollisionCache.state(accel, pos);
    }

    @ModifyExpressionValue(
            method = "collide",
            at = @At(value = "CONSTANT", args = "doubleValue=0.01"),
            remap = false
    )
    private static double pym$relativeInteriorContactTolerance(
            double vanillaTolerance,
            @Local(name = "compressedMinAABB") BoundingBox3d compressedMinAABB,
            @Local(name = "subLevel") SubLevel subLevel,
            @Local(argsOnly = true) net.minecraft.world.entity.Entity entity
    ) {
        if (!ScaleState.isScaled(subLevel)
                && ScaleBounds.same(Pym.entities().scaleOf(entity), ScaleBounds.FULL)) {
            return vanillaTolerance;
        }
        return InteriorCollisionTolerance.forShape(vanillaTolerance, compressedMinAABB.volume());
    }

    @ModifyExpressionValue(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Vector3d;lengthSquared()D",
                    ordinal = 3
            ),
            remap = false
    )
    private static double pym$breakEmptyMaxIter(
            double value,
            @Local(name = "maxIter") LocalIntRef maxIter,
            @Local(name = "subLevel") SubLevel subLevel,
            @Local(argsOnly = true) net.minecraft.world.entity.Entity entity
    ) {
        if ((ScaleState.isScaled(subLevel)
                || !ScaleBounds.same(Pym.entities().scaleOf(entity), ScaleBounds.FULL))
                && value <= 0.0) maxIter.set(4);
        return value;
    }

    @WrapOperation(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;sat(Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false,
            require = 1
    )
    private static Vector3d pym$pushPastThinColliders(
            OrientedBoundingBox3d entityOBB, OrientedBoundingBox3d cubeOBB, Vector3d mtv,
            Operation<Vector3d> original,
            @Local(name = "collisionMotion") Vector3d motion
    ) {
        Vector3d result = original.call(entityOBB, cubeOBB, mtv);
        double length = result.length();
        if (!(length > 0.0) || !Double.isFinite(length)) return result;
        double into = -motion.dot(result) / length;
        if (!(into > length)) return result;
        Vector3d normal = new Vector3d(result).div(length);
        double depth = cubeOBB.getPosition().dot(normal) + pym$reach(cubeOBB, normal)
                - entityOBB.getPosition().dot(normal) + pym$reach(entityOBB, normal);
        double push = Math.min(into, depth);
        if (push > length) result.set(normal.mul(push));
        return result;
    }

    @Unique
    private static double pym$reach(OrientedBoundingBox3d box, Vector3d direction) {
        Vector3d local = box.getOrientation().transformInverse(new Vector3d(direction));
        Vector3d size = box.getDimensions();
        return 0.5 * (Math.abs(local.x) * size.x + Math.abs(local.y) * size.y + Math.abs(local.z) * size.z);
    }

    @Inject(method = "collide", at = @At("HEAD"), remap = false)
    private static void pym$beginCollisionLog(CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> info) {
        CollisionLog.begin();
    }

    @Inject(method = "collide", at = @At("RETURN"), remap = false)
    private static void pym$logCollisionCost(Entity entity, net.minecraft.world.phys.Vec3 motion, net.minecraft.world.phys.Vec3 velocity,
                                             LevelReusedVectors sink, CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> info) {
        CollisionLog.end(entity);
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
    @WrapOperation(
            method = "collide",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision;getSubLevelEntityCollisionShape(Lnet/minecraft/world/entity/Entity;Lorg/joml/Vector3dc;Ldev/ryanhcode/sable/companion/math/Pose3dc;Lnet/minecraft/world/level/block/state/BlockState;Ldev/ryanhcode/sable/util/LevelAccelerator;Lnet/minecraft/core/BlockPos;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Lnet/minecraft/world/phys/shapes/VoxelShape;", ordinal = 0),
            remap = false,
            require = 1
    )
    private static VoxelShape pym$mainShape(
            Entity entity, Vector3dc center, Pose3dc pose, BlockState state,
            LevelAccelerator accel, BlockPos pos, LevelReusedVectors sink,
            Operation<VoxelShape> original
    ) {
        VoxelShape shape = SubLevelIndexedCollision.shape(pos);
        if (shape == null) shape = original.call(entity, center, pose, state, accel, pos, sink);
        if (CollisionTrace.enabled()) CollisionTrace.record(entity, state, pos, shape, SubLevelIndexedCollision.lod());
        return shape;
    }

    @WrapOperation(
            method = "collide",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision;getSubLevelEntityCollisionShape(Lnet/minecraft/world/entity/Entity;Lorg/joml/Vector3dc;Ldev/ryanhcode/sable/companion/math/Pose3dc;Lnet/minecraft/world/level/block/state/BlockState;Ldev/ryanhcode/sable/util/LevelAccelerator;Lnet/minecraft/core/BlockPos;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Lnet/minecraft/world/phys/shapes/VoxelShape;", ordinal = 1),
            remap = false,
            require = 1
    )
    private static VoxelShape pym$interiorShape(
            Entity entity, Vector3dc center, Pose3dc pose, BlockState state,
            LevelAccelerator accel, BlockPos pos, LevelReusedVectors sink,
            Operation<VoxelShape> original,
            @Local(name = "maxBlockPos") BlockPos.MutableBlockPos maxBlockPos
    ) {
        return SubLevelIndexedCollision.proxy(maxBlockPos) ? net.minecraft.world.phys.shapes.Shapes.empty()
                : original.call(entity, center, pose, state, accel, pos, sink);
    }

    @WrapOperation(
            method = "hasCollision",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
            remap = false,
            require = 1
    )
    private static VoxelShape pym$stepShape(BlockState state, BlockGetter level, BlockPos pos,
                                            Operation<VoxelShape> original) {
        VoxelShape shape = SubLevelIndexedCollision.shape(pos);
        return shape != null ? shape : original.call(state, level, pos);
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;dot(Lorg/joml/Vector3dc;)D", ordinal = 1),
            remap = false,
            require = 1
    )
    private static double pym$stepSupport(
            double dot,
            @Local(argsOnly = true) LevelAccelerator accel,
            @Local(argsOnly = true) LevelReusedVectors sink,
            @Local(argsOnly = true) Pose3dc pose,
            @Local(argsOnly = true, ordinal = 0) Vector3dc entityCenter,
            @Local(argsOnly = true, ordinal = 2) Vector3dc normal,
            @Local(argsOnly = true) AABB entityBounds,
            @Local(argsOnly = true) Entity entity
    ) {
        if (dot > 0.8 || (dot < -0.25 && pym$subLevelScale(pose) > 0.0625)) return dot;
        boolean supported = pym$hasLandingSurface(entity, accel, sink, pose, entityCenter, normal, entityBounds);
        return supported ? 1.0 : dot;
    }

    @Unique
    private static boolean pym$hasLandingSurface(
            Entity entity, LevelAccelerator accel, LevelReusedVectors sink, Pose3dc pose,
            Vector3dc entityCenter, Vector3dc normal, AABB bounds
    ) {
        Vector3d up = new Vector3d(sink.entityUpDirection).normalize();
        Vector3d localUp = pose.transformNormalInverse(new Vector3d(up)).normalize();
        if (localUp.y < 0.999) return false;

        Vector3d forward = new Vector3d(normal).fma(-normal.dot(up), up).negate();
        if (forward.lengthSquared() < 0.25) return false;

        double halfHeight = bounds.getYsize() * 0.5;
        Vector3d low = new Vector3d(entityCenter).fma(-halfHeight, up);
        Vector3d high = new Vector3d(sink.stepHeightEntityBoundsCenter).fma(-halfHeight, up);
        pose.transformPositionInverse(low);
        pose.transformPositionInverse(high);
        if (!Double.isFinite(low.y) || !Double.isFinite(high.y)
                || high.y <= low.y || high.y - low.y > 64.0) return false;

        double halfX = bounds.getXsize() * 0.5;
        double halfZ = bounds.getZsize() * 0.5;
        Vector3d center = sink.stepHeightEntityBoundsCenter;
        double[] xs = new double[4];
        double[] zs = new double[4];
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            Vector3d corner = new Vector3d(center).add(
                    i == 0 || i == 3 ? -halfX : halfX, 0.0,
                    i < 2 ? -halfZ : halfZ);
            pose.transformPositionInverse(corner);
            xs[i] = corner.x;
            zs[i] = corner.z;
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX)
                || !Double.isFinite(minZ) || !Double.isFinite(maxZ)) return false;

        double feet = pose.transformPositionInverse(new Vector3d(entity.getX(), entity.getY(), entity.getZ())).y;
        return SubLevelIndexedCollision.support(accel, xs, zs, Math.max(low.y, feet + 0.05), high.y);
    }

    @WrapOperation(
            method = "collide",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision;tryStepUp(Lnet/minecraft/world/entity/Entity;Ldev/ryanhcode/sable/util/LevelAccelerator;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;Ldev/ryanhcode/sable/companion/math/Pose3dc;Ljava/lang/Iterable;Lorg/joml/Vector3dc;Lnet/minecraft/world/phys/AABB;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Z"),
            remap = false,
            require = 1
    )
    private static boolean pym$stepWithoutWallCorrection(
            net.minecraft.world.entity.Entity entity, LevelAccelerator accel, LevelReusedVectors sink,
            Pose3dc pose, Iterable<BlockPos> blocks, Vector3dc center, AABB bounds,
            OrientedBoundingBox3d entityOBB, OrientedBoundingBox3d cubeOBB,
            Vector3dc maxMTV, Vector3dc normal, Vector3d motion, Operation<Boolean> original
    ) {
        Vector3d before = new Vector3d(motion);
        Vector3d up = new Vector3d(sink.entityUpDirection);
        if (up.lengthSquared() > 0.0) up.normalize();

        if (SubLevelIndexedCollision.active() && entity.onGround() && up.lengthSquared() > 0.0
                && motion.dot(normal) <= 0.0 && Double.isFinite(entity.maxUpStep())
                && entity.maxUpStep() > 0.0) {
            double step = entity.maxUpStep();
            Vector3d target = new Vector3d(center).add(motion);
            Vector3d candidate = new Vector3d(target);
            Vector3d foot = new Vector3d(target).fma(pym$subLevelScale(pose) * 0.04, up);
            sink.stepHeightEntityBoundsCenter.set(target).fma(step, up);
            if (pym$hasLandingSurface(entity, accel, sink, pose, foot, normal, bounds)
                    && hasCollision(accel, sink, pose, blocks, entityOBB, cubeOBB, candidate)) {
                double lower = 0.0;
                boolean blocked = true;
                for (int i = 1; i <= 16; i++) {
                    double upper = step * i / 16.0;
                    candidate.set(target).fma(upper, up);
                    if (hasCollision(accel, sink, pose, blocks, entityOBB, cubeOBB, candidate)) {
                        lower = upper;
                        continue;
                    }
                    blocked = false;
                    for (int j = 0; j < 10; j++) {
                        double mid = (lower + upper) * 0.5;
                        candidate.set(target).fma(mid, up);
                        if (hasCollision(accel, sink, pose, blocks, entityOBB, cubeOBB, candidate)) lower = mid;
                        else upper = mid;
                    }
                    double rise = Math.min(step, upper + Math.min(1.0E-4, step * 1.0E-3));
                    candidate.set(target).fma(rise, up);
                    sink.stepHeightEntityBoundsCenter.set(candidate);
                    if (!hasCollision(accel, sink, pose, blocks, entityOBB, cubeOBB, candidate)
                            && pym$hasLandingSurface(entity, accel, sink, pose, foot, normal, bounds)) {
                        motion.fma(rise, up);
                        sink.maxMTV.zero();
                        return true;
                    }
                    lower = upper;
                }
                if (blocked) return false;
            }
        }

        boolean stepped = original.call(entity, accel, sink, pose, blocks, center, bounds,
                entityOBB, cubeOBB, maxMTV, normal, motion);
        if (stepped && motion.dot(up) - before.dot(up) > 1.0E-6) {
            Vector3d candidate = new Vector3d(center).add(motion);
            if (hasCollision(accel, sink, pose, blocks, entityOBB, cubeOBB, candidate)) {
                motion.set(before);
                return false;
            }
            sink.maxMTV.zero();
        }
        return stepped;
    }

    @ModifyExpressionValue(
            method = "tryStepUp",
            at = @At(value = "CONSTANT", args = "doubleValue=0.0625"),
            remap = false
    )
    private static double pym$scaleStepIncrement(
            double value,
            @Local(argsOnly = true) Pose3dc subLevelPose,
            @Local(argsOnly = true) Entity entity
    ) {
        return Math.max(value * pym$subLevelScale(subLevelPose), entity.maxUpStep() / 48.0);
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
