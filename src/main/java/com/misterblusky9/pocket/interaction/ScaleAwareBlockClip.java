package com.misterblusky9.pocket.interaction;

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
import com.misterblusky9.pocket.mixin.interaction.ClipContextAccessor;
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

    public static BlockHitResult clip(final BlockGetter self, ClipContext context) {
        if (!(self instanceof final Level level)) {
            return vanillaClip(self, context);
        }

        final ClipContextExtension sableContext =
                context instanceof final ClipContextExtension extension ? extension : null;

        if (sableContext != null && sableContext.sable$doNotProject()) {
            return vanillaClip(self, context);
        }

        final SubLevel ignoredSubLevel =
                sableContext != null ? sableContext.sable$getIgnoredSubLevel() : null;
        final Predicate<SubLevel> ignoredPredicate =
                sableContext != null ? sableContext.sable$getSubLevelIgnoring() : null;
        final boolean ignoreMainLevel =
                sableContext != null && sableContext.sable$isIgnoreMainLevel();

        final ActiveSableCompanion helper = Sable.HELPER;

        final SubLevel fromSubLevel = helper.getContaining(level, context.getFrom());
        if (fromSubLevel != null) {
            final Pose3dc pose = poseFor(level, fromSubLevel);
            final Vector3dc projected =
                    pose.transformPosition(JOMLConversion.toJOML(context.getFrom()));
            context = copyWithEndpoints(
                    context,
                    JOMLConversion.toMojang(projected),
                    context.getTo()
            );
        }

        final SubLevel toSubLevel = helper.getContaining(level, context.getTo());
        if (toSubLevel != null) {
            final Pose3dc pose = poseFor(level, toSubLevel);
            final Vector3dc projected =
                    pose.transformPosition(JOMLConversion.toJOML(context.getTo()));
            context = copyWithEndpoints(
                    context,
                    context.getFrom(),
                    JOMLConversion.toMojang(projected)
            );
        }

        final Vec3 worldRayFrom = context.getFrom();

        BlockHitResult bestResult;
        double bestWorldDistance;

        if (ignoreMainLevel) {
            final Vec3 diff = context.getFrom().subtract(context.getTo());
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

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return bestResult;
        }

        final Vec3 rayFrom = context.getFrom();
        final Vec3 rayTo = context.getTo();

        double[] range = null;

        for (final SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == ignoredSubLevel) {
                continue;
            }
            if (ignoredPredicate != null && ignoredPredicate.test(subLevel)) {
                continue;
            }

            if (!segmentIntersectsWorldBounds(rayFrom, rayTo, subLevel)) {
                continue;
            }

            final Pose3dc pose = poseFor(level, subLevel);

            final Vector3dc localFromJoml =
                    pose.transformPositionInverse(JOMLConversion.toJOML(context.getFrom()));
            final Vector3dc localToJoml =
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

            final Vec3 localFrom;
            final Vec3 localTo;
            final double localLength = localFromJoml.distance(localToJoml);

            if (localLength > TRIM_THRESHOLD_BLOCKS) {
                final double pad = TRIM_PADDING_BLOCKS / localLength;
                localFrom = pointAlong(localFromJoml, localToJoml, Math.max(0.0D, range[0] - pad));
                localTo = pointAlong(localFromJoml, localToJoml, Math.min(1.0D, range[1] + pad));
            } else {
                localFrom = JOMLConversion.toMojang(localFromJoml);
                localTo = JOMLConversion.toMojang(localToJoml);
            }

            final ClipContext localContext =
                    copyWithEndpoints(context, localFrom, localTo);

            final BlockHitResult candidate =
                    vanillaClip(subLevel.getLevel(), localContext);
            if (candidate.getType() == HitResult.Type.MISS) {
                continue;
            }

            final Vector3dc candidateWorldJoml =
                    pose.transformPosition(JOMLConversion.toJOML(candidate.getLocation()));
            final Vec3 candidateWorld =
                    JOMLConversion.toMojang(candidateWorldJoml);
            final double candidateWorldDistance =
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
            final Vec3 from,
            final Vec3 to,
            final SubLevel subLevel
    ) {
        final BoundingBox3dc bounds = subLevel.boundingBox();
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
            final Vector3dc from,
            final Vector3dc to,
            final BoundingBox3ic bounds,
            final double[] range
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
            final Vector3dc from,
            final Vector3dc to,
            final double t
    ) {
        return new Vec3(
                from.x() + (to.x() - from.x()) * t,
                from.y() + (to.y() - from.y()) * t,
                from.z() + (to.z() - from.z()) * t
        );
    }

    private static boolean segmentIntersectsBox(
            final double fromX, final double fromY, final double fromZ,
            final double toX, final double toY, final double toZ,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final double[] range
    ) {
        double tMin = 0.0D;
        double tMax = 1.0D;

        final double deltaX = toX - fromX;
        if (Math.abs(deltaX) <= 1.0E-12D) {
            if (fromX < minX || fromX > maxX) return false;
        } else {
            double t1 = (minX - fromX) / deltaX;
            double t2 = (maxX - fromX) / deltaX;
            if (t1 > t2) { final double swap = t1; t1 = t2; t2 = swap; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        final double deltaY = toY - fromY;
        if (Math.abs(deltaY) <= 1.0E-12D) {
            if (fromY < minY || fromY > maxY) return false;
        } else {
            double t1 = (minY - fromY) / deltaY;
            double t2 = (maxY - fromY) / deltaY;
            if (t1 > t2) { final double swap = t1; t1 = t2; t2 = swap; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) return false;
        }

        final double deltaZ = toZ - fromZ;
        if (Math.abs(deltaZ) > 1.0E-12D) {
            double t1 = (minZ - fromZ) / deltaZ;
            double t2 = (maxZ - fromZ) / deltaZ;
            if (t1 > t2) { final double swap = t1; t1 = t2; t2 = swap; }
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
            final Level level,
            final SubLevel subLevel
    ) {
        if (level instanceof final LevelPoseProviderExtension extension) {
            return extension.sable$getPose(subLevel);
        }
        return subLevel.logicalPose();
    }

    private static ClipContext copyWithEndpoints(
            final ClipContext source,
            final Vec3 from,
            final Vec3 to
    ) {
        final ClipContextAccessor accessor = (ClipContextAccessor) source;
        return new ClipContext(
                from,
                to,
                accessor.pocket$getBlockMode(),
                accessor.pocket$getFluidMode(),
                accessor.pocket$getCollisionContext()
        );
    }

    private static BlockHitResult vanillaClip(
            final BlockGetter level,
            final ClipContext context
    ) {
        return BlockGetter.traverseBlocks(
                context.getFrom(),
                context.getTo(),
                context,
                (ctx, pos) -> {
                    final BlockState blockState = level.getBlockState(pos);
                    final FluidState fluidState = level.getFluidState(pos);
                    final Vec3 from = ctx.getFrom();
                    final Vec3 to = ctx.getTo();

                    final VoxelShape blockShape =
                            ctx.getBlockShape(blockState, level, pos);
                    final BlockHitResult blockHit =
                            level.clipWithInteractionOverride(
                                    from, to, pos, blockShape, blockState
                            );

                    final VoxelShape fluidShape =
                            ctx.getFluidShape(fluidState, level, pos);
                    final BlockHitResult fluidHit =
                            fluidShape.clip(from, to, pos);

                    final double blockDistanceSq = blockHit == null
                            ? Double.MAX_VALUE
                            : from.distanceToSqr(blockHit.getLocation());
                    final double fluidDistanceSq = fluidHit == null
                            ? Double.MAX_VALUE
                            : from.distanceToSqr(fluidHit.getLocation());

                    return blockDistanceSq <= fluidDistanceSq ? blockHit : fluidHit;
                },
                ctx -> {
                    final Vec3 diff = ctx.getFrom().subtract(ctx.getTo());
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
