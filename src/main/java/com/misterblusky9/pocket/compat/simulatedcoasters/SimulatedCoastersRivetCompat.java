package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.persistence.ScalePersistence;
import com.misterblusky9.pocket.physics.ScaleFrame;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SimulatedCoastersRivetCompat {
    private static final String NAMESPACE = "simulatedcoasters";
    private static final String RIVET_PATH = "rivet";
    private static final String PLACEMENT_SCALE_INITIALIZED = "pocket_rivet_scale_initialized";
    private static final double FRAME_DISTANCE = 3.0D;
    private static final double MATCH_EPSILON_SQUARED = 1.0E-10D;

    public static boolean isRivetSubLevel(final PhysicsPipelineBody body) {
        return body instanceof final ServerSubLevel subLevel && isRivetSubLevel(subLevel);
    }

    public static boolean isRivetSubLevel(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return false;

        try {
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(
                    subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).getBlock());
            return NAMESPACE.equals(id.getNamespace()) && RIVET_PATH.equals(id.getPath());
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    public static void initializePlacementScale(
            final PhysicsPipelineBody body1,
            final PhysicsPipelineBody body2
    ) {
        if (!(body2 instanceof final ServerSubLevel rivet) || !isRivetSubLevel(rivet)) return;
        if (placementScaleInitialized(rivet)) return;

        markPlacementScaleInitialized(rivet);

        if (!(body1 instanceof final ServerSubLevel host) || host.isRemoved()) return;

        final double targetScale = PocketSized.clampScale(ScaleFrame.scaleOf(host));
        final double previousScale = PocketSized.clampScale(ScaleFrame.scaleOf(rivet));
        if (Math.abs(targetScale - previousScale) <= PocketSized.EPSILON) return;

        final Vector3d anchor = attachmentAnchor(rivet);
        final Vector3d worldAnchor = worldPoint(rivet, anchor, previousScale);
        rivet.logicalPose().scale().set(targetScale, targetScale, targetScale);

        final Vector3d relative = localRelativeWorld(rivet, anchor, targetScale);
        final Vector3d correctedPosition = new Vector3d(worldAnchor).sub(relative);
        rivet.logicalPose().position().set(correctedPosition);
        rivet.updateBoundingBox();

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(rivet.getLevel());
        if (container != null) {
            container.physicsSystem().getPipeline().teleport(
                    rivet, correctedPosition, rivet.logicalPose().orientation());
        }

        final CompressionStage stage = CompressionStage.nearest(targetScale);
        final ScaleState.ServerState state = ScaleState.restoreServerState(
                rivet, targetScale, stage, stage, null);
        rivet.updateLastPose();
        ScalePersistence.persist(rivet, state);
        ScaleNetwork.sendScale(rivet, targetScale, targetScale, true);
    }

    public static Vector3d attachmentAnchor(final ServerSubLevel subLevel) {
        final BlockPos center = subLevel.getPlot().getCenterBlock();
        return new Vector3d(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D);
    }

    public static boolean requiresRefresh(
            final FixedConstraintConfiguration fixed,
            final PhysicsPipelineBody body1,
            final double bakedScale1,
            final PhysicsPipelineBody body2,
            final double bakedScale2
    ) {
        if (fixed == null || !isRivetSubLevel(body2)) return false;
        if (classify((ServerSubLevel) body2, body1, fixed.pos2()) == Kind.NONE) return false;

        if (Math.abs(ScaleFrame.scaleOf(body2) - bakedScale2) > PocketSized.EPSILON) return true;
        return body1 != null && Math.abs(ScaleFrame.scaleOf(body1) - bakedScale1) > PocketSized.EPSILON;
    }

    public static FixedConstraintConfiguration transform(
            final PhysicsPipelineBody body1,
            final PhysicsPipelineBody body2,
            final FixedConstraintConfiguration fixed
    ) {
        if (fixed == null || !(body2 instanceof final ServerSubLevel rivet) || !isRivetSubLevel(rivet)) {
            return null;
        }

        final Vector3d rivetBasePlot = attachmentAnchor(rivet);
        final Kind kind = classify(rivet, body1, fixed.pos2());
        if (kind == Kind.NONE) return null;

        final Vector3d rivetBaseMetric = new Vector3d(ScaleFrame.toBodyMetric(rivet, rivetBasePlot));

        if (kind == Kind.BASE_WORLD) {
            return new FixedConstraintConfiguration(
                    worldPoint(rivet, rivetBasePlot),
                    rivetBaseMetric,
                    fixed.orientation()
            );
        }

        if (kind == Kind.BASE) {
            return new FixedConstraintConfiguration(
                    ScaleFrame.toBodyMetric(body1, fixed.pos1()),
                    rivetBaseMetric,
                    fixed.orientation()
            );
        }

        final Vector3d rivetAxis = kind == Kind.DISTAL
                ? new Vector3d(0.0D, 1.0D, 0.0D)
                : new Vector3d(1.0D, 0.0D, 0.0D);
        final Vector3d rivetMetric = new Vector3d(rivetBaseMetric).fma(FRAME_DISTANCE, rivetAxis);

        if (body1 == null) {
            return new FixedConstraintConfiguration(
                    fixed.pos1(),
                    rivetMetric,
                    fixed.orientation()
            );
        }

        final Vector3d hostAxis = new Vector3d(rivetAxis);
        new Quaterniond(fixed.orientation()).transform(hostAxis);
        if (hostAxis.lengthSquared() > 1.0E-20D) hostAxis.normalize();

        final Vector3d hostBasePlot = new Vector3d(fixed.pos1()).fma(-FRAME_DISTANCE, hostAxis);
        final Vector3d hostMetric = new Vector3d(ScaleFrame.toBodyMetric(body1, hostBasePlot))
                .fma(FRAME_DISTANCE, hostAxis);

        return new FixedConstraintConfiguration(
                hostMetric,
                rivetMetric,
                fixed.orientation()
        );
    }

    private static boolean placementScaleInitialized(final ServerSubLevel rivet) {
        final CompoundTag tag = rivet.getUserDataTag();
        return tag != null && tag.getBoolean(PLACEMENT_SCALE_INITIALIZED);
    }

    private static void markPlacementScaleInitialized(final ServerSubLevel rivet) {
        CompoundTag tag = rivet.getUserDataTag();
        if (tag == null) tag = new CompoundTag();
        tag.putBoolean(PLACEMENT_SCALE_INITIALIZED, true);
        rivet.setUserDataTag(tag);
    }

    private static Kind classify(
            final ServerSubLevel rivet,
            final PhysicsPipelineBody body1,
            final Vector3dc rivetPoint
    ) {
        if (rivetPoint == null) return Kind.NONE;

        final Vector3d base = attachmentAnchor(rivet);
        final Vector3d distal = new Vector3d(base).add(0.0D, FRAME_DISTANCE, 0.0D);
        final Vector3d lateral = new Vector3d(base).add(FRAME_DISTANCE, 0.0D, 0.0D);

        if (rivetPoint.distanceSquared(distal) <= MATCH_EPSILON_SQUARED) return Kind.DISTAL;
        if (rivetPoint.distanceSquared(lateral) <= MATCH_EPSILON_SQUARED) return Kind.LATERAL;
        if (rivetPoint.distanceSquared(base) <= MATCH_EPSILON_SQUARED) return Kind.BASE;

        final Vector3dc pivot = ScaleFrame.pivot(rivet);
        if (body1 == null && pivot != null
                && rivetPoint.distanceSquared(pivot) <= MATCH_EPSILON_SQUARED) {
            return Kind.BASE_WORLD;
        }
        return Kind.NONE;
    }

    private static Vector3d worldPoint(final ServerSubLevel subLevel, final Vector3dc plotPoint) {
        return worldPoint(subLevel, plotPoint, ScaleFrame.scaleOf(subLevel));
    }

    private static Vector3d worldPoint(
            final ServerSubLevel subLevel,
            final Vector3dc plotPoint,
            final double scale
    ) {
        return localRelativeWorld(subLevel, plotPoint, scale).add(subLevel.logicalPose().position());
    }

    private static Vector3d localRelativeWorld(
            final ServerSubLevel subLevel,
            final Vector3dc plotPoint,
            final double scale
    ) {
        final Vector3d relative = new Vector3d(plotPoint)
                .sub(subLevel.logicalPose().rotationPoint())
                .mul(scale);
        subLevel.logicalPose().orientation().transform(relative);
        return relative;
    }

    private enum Kind {
        NONE,
        BASE,
        BASE_WORLD,
        DISTAL,
        LATERAL
    }

    private SimulatedCoastersRivetCompat() {}
}
