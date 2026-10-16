package com.misterblusky9.pym.internal.interaction;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pym.internal.mixin.interaction.ClipContextAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;

import java.util.function.Predicate;

public final class ScaleAwareBlockClip {
    private static final double WORLD_HIT_TIE_EPSILON = 1.0E-4D;

    private static final double LOCAL_BOUNDS_EPSILON = 1.0E-5D;

    private static final double WORLD_BROADPHASE_PADDING = 0.05D;

    private static final double TRIM_THRESHOLD_BLOCKS = 32.0D;

    private static final double TRIM_PADDING_BLOCKS = 2.0D;

    public static BlockHitResult clip(BlockGetter self, ClipContext context) {
        if (!(self instanceof Level level)) {
            return vanillaClip(self, context);
        }

        ClipContextExtension sableContext =
                context instanceof ClipContextExtension extension ? extension : null;

        if (sableContext != null && sableContext.sable$doNotProject()) {
            return vanillaClip(self, context);
        }

        SubLevel ignoredSubLevel =
                sableContext != null ? sableContext.sable$getIgnoredSubLevel() : null;
        Predicate<SubLevel> ignoredPredicate =
                sableContext != null ? sableContext.sable$getSubLevelIgnoring() : null;
        boolean ignoreMainLevel =
                sableContext != null && sableContext.sable$isIgnoreMainLevel();

        ActiveSableCompanion helper = Sable.HELPER;

        SubLevel fromSubLevel = helper.getContaining(level, context.getFrom());
        if (fromSubLevel != null) {
            Pose3dc pose = poseFor(level, fromSubLevel);
            Vector3dc projected =
                    pose.transformPosition(JOMLConversion.toJOML(context.getFrom()));
            context = copyWithEndpoints(
                    context,
                    JOMLConversion.toMojang(projected),
                    context.getTo()
            );
        }

        SubLevel toSubLevel = helper.getContaining(level, context.getTo());
        if (toSubLevel != null) {
            Pose3dc pose = poseFor(level, toSubLevel);
            Vector3dc projected =
                    pose.transformPosition(JOMLConversion.toJOML(context.getTo()));
            context = copyWithEndpoints(
                    context,
                    context.getFrom(),
                    JOMLConversion.toMojang(projected)
            );
        }

        Vec3 worldRayFrom = context.getFrom();

        BlockHitResult bestResult;
        double bestWorldDistance;

        if (ignoreMainLevel) {
            Vec3 diff = context.getFrom().subtract(context.getTo());
            bestResult = BlockHitResult.miss(
                    context.getTo(),
                    Direction.getNearest(diff.x, diff.y, diff.z),
                    BlockPos.containing(context.getTo())
            );
            bestWorldDistance = Double.MAX_VALUE;
        } else {
            bestResult = vanillaClip(self, context);
            bestWorldDistance = bestResult.getType() == HitResult.Type.MISS
                    ? Double.MAX_VALUE
                    : bestResult.getLocation().distanceTo(worldRayFrom);
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return bestResult;
        }

        Vec3 rayFrom = context.getFrom();
        Vec3 rayTo = context.getTo();

        double[] range = null;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == ignoredSubLevel) {
                continue;
            }
            if (ignoredPredicate != null && ignoredPredicate.test(subLevel)) {
                continue;
            }

            if (!segmentIntersectsWorldBounds(rayFrom, rayTo, subLevel)) {
                continue;
            }

            Pose3dc pose = poseFor(level, subLevel);

            Vector3dc localFromJoml =
                    pose.transformPositionInverse(JOMLConversion.toJOML(context.getFrom()));
            Vector3dc localToJoml =
                    pose.transformPositionInverse(JOMLConversion.toJOML(context.getTo()));

            if (range == null) range = new double[2];
            if (!rayIntersectsPlotBounds(
                    localFromJoml,
                    localToJoml,
                    subLevel.getPlot().getBoundingBox(),
                    range
            )) {
                continue;
            }

            Vec3 localFrom;
            Vec3 localTo;
            double localLength = localFromJoml.distance(localToJoml);

            if (localLength > TRIM_THRESHOLD_BLOCKS) {
                double pad = TRIM_PADDING_BLOCKS / localLength;
                localFrom = pointAlong(localFromJoml, localToJoml, Math.max(0.0D, range[0] - pad));
                localTo = pointAlong(localFromJoml, localToJoml, Math.min(1.0D, range[1] + pad));
            } else {
                localFrom = JOMLConversion.toMojang(localFromJoml);
                localTo = JOMLConversion.toMojang(localToJoml);
            }

            ClipContext localContext =
                    copyWithEndpoints(context, localFrom, localTo);

            BlockHitResult candidate =
                    vanillaClip(subLevel.getLevel(), localContext);
            if (candidate.getType() == HitResult.Type.MISS) {
                continue;
            }

            Vector3dc candidateWorldJoml =
                    pose.transformPosition(JOMLConversion.toJOML(candidate.getLocation()));
            Vec3 candidateWorld =
                    JOMLConversion.toMojang(candidateWorldJoml);
            double candidateWorldDistance =
                    candidateWorld.distanceTo(worldRayFrom);

            if (candidateWorldDistance <= bestWorldDistance + WORLD_HIT_TIE_EPSILON
                    || bestResult.getType() == HitResult.Type.MISS) {
                bestResult = candidate;
                bestWorldDistance = candidateWorldDistance;
            }
        }

        return bestResult;
    }

    private static boolean segmentIntersectsWorldBounds(
            Vec3 from,
            Vec3 to,
            SubLevel subLevel
    ) {
        BoundingBox3dc bounds = subLevel.boundingBox();
        if (bounds == null) return true;

        return segmentIntersectsBox(
                from.x, from.y, from.z,
                to.x, to.y, to.z,
                bounds.minX() - WORLD_BROADPHASE_PADDING,
                bounds.minY() - WORLD_BROADPHASE_PADDING,
                bounds.minZ() - WORLD_BROADPHASE_PADDING,
                bounds.maxX() + WORLD_BROADPHASE_PADDING,
                bounds.maxY() + WORLD_BROADPHASE_PADDING,
                bounds.maxZ() + WORLD_BROADPHASE_PADDING,
                null
        );
    }

    private static boolean rayIntersectsPlotBounds(
            Vector3dc from,
            Vector3dc to,
            BoundingBox3ic bounds,
            double[] range
    ) {
        if (bounds == null) return false;

        return segmentIntersectsBox(
                from.x(), from.y(), from.z(),
                to.x(), to.y(), to.z(),
                bounds.minX() - LOCAL_BOUNDS_EPSILON,
                bounds.minY() - LOCAL_BOUNDS_EPSILON,
                bounds.minZ() - LOCAL_BOUNDS_EPSILON,
                bounds.maxX() + 1.0D + LOCAL_BOUNDS_EPSILON,
                bounds.maxY() + 1.0D + LOCAL_BOUNDS_EPSILON,
                bounds.maxZ() + 1.0D + LOCAL_BOUNDS_EPSILON,
                range
        );
    }

    private static Vec3 pointAlong(
            Vector3dc from,
            Vector3dc to,
            double t
    ) {
        return new Vec3(
                from.x() + (to.x() - from.x()) * t,
                from.y() + (to.y() - from.y()) * t,
                from.z() + (to.z() - from.z()) * t
        );
    }

    private static boolean segmentIntersectsBox(
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double[] range
    ) {
        double tMin = 0.0D;
        double tMax = 1.0D;

        double deltaX = toX - fromX;
        if (Math.abs(deltaX) <= 1.0E-12D) {
            if (fromX < minX || fromX > maxX) return false;
        } else {
            double t1 = (minX - fromX) / deltaX;
            double t2 = (maxX - fromX) / deltaX;
            if (t1 > t2) { double swap = t1; t1 = t2; t2 = swap; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        double deltaY = toY - fromY;
        if (Math.abs(deltaY) <= 1.0E-12D) {
            if (fromY < minY || fromY > maxY) return false;
        } else {
            double t1 = (minY - fromY) / deltaY;
            double t2 = (maxY - fromY) / deltaY;
            if (t1 > t2) { double swap = t1; t1 = t2; t2 = swap; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        double deltaZ = toZ - fromZ;
        if (Math.abs(deltaZ) > 1.0E-12D) {
            double t1 = (minZ - fromZ) / deltaZ;
            double t2 = (maxZ - fromZ) / deltaZ;
            if (t1 > t2) { double swap = t1; t1 = t2; t2 = swap; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        } else if (fromZ < minZ || fromZ > maxZ) {
            return false;
        }

        if (range != null) {
            range[0] = tMin;
            range[1] = tMax;
        }
        return true;
    }

    private static Pose3dc poseFor(
            Level level,
            SubLevel subLevel
    ) {
        if (level instanceof LevelPoseProviderExtension extension) {
            return extension.sable$getPose(subLevel);
        }
        return subLevel.logicalPose();
    }

    private static ClipContext copyWithEndpoints(
            ClipContext source,
            Vec3 from,
            Vec3 to
    ) {
        ClipContextAccessor accessor = (ClipContextAccessor) source;
        return new ClipContext(
                from,
                to,
                accessor.pym$getBlockMode(),
                accessor.pym$getFluidMode(),
                accessor.pym$getCollisionContext()
        );
    }

    private static BlockHitResult vanillaClip(
            BlockGetter level,
            ClipContext context
    ) {
        return BlockGetter.traverseBlocks(
                context.getFrom(),
                context.getTo(),
                context,
                (ctx, pos) -> {
                    BlockState blockState = level.getBlockState(pos);
                    FluidState fluidState = level.getFluidState(pos);
                    Vec3 from = ctx.getFrom();
                    Vec3 to = ctx.getTo();

                    VoxelShape blockShape =
                            ctx.getBlockShape(blockState, level, pos);
                    BlockHitResult blockHit =
                            level.clipWithInteractionOverride(
                                    from, to, pos, blockShape, blockState
                            );

                    VoxelShape fluidShape =
                            ctx.getFluidShape(fluidState, level, pos);
                    BlockHitResult fluidHit =
                            fluidShape.clip(from, to, pos);

                    double blockDistanceSq = blockHit == null
                            ? Double.MAX_VALUE
                            : from.distanceToSqr(blockHit.getLocation());
                    double fluidDistanceSq = fluidHit == null
                            ? Double.MAX_VALUE
                            : from.distanceToSqr(fluidHit.getLocation());

                    return blockDistanceSq <= fluidDistanceSq ? blockHit : fluidHit;
                },
                ctx -> {
                    Vec3 diff = ctx.getFrom().subtract(ctx.getTo());
                    return BlockHitResult.miss(
                            ctx.getTo(),
                            Direction.getNearest(diff.x, diff.y, diff.z),
                            BlockPos.containing(ctx.getTo())
                    );
                }
        );
    }

    private ScaleAwareBlockClip() {
    }
}
