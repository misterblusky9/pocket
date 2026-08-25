package com.misterblusky9.pocket.persistence;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector3d;

public final class ScalePersistence {
    private static final String ROOT_KEY = "pocket_scale";
    private static final String CURRENT_KEY = "current";
    private static final String STABLE_STAGE_KEY = "stable_stage";
    private static final String REQUESTED_STAGE_KEY = "requested_stage";
    private static final String TRANSITION_STAGE_KEY = "transition_stage";
    private static final String LEGACY_TARGET_KEY = "manual_target";

    public static void persist(final ServerSubLevel subLevel, final ScaleState.ServerState state) {
        if (subLevel == null || state == null) return;

        CompoundTag userData = subLevel.getUserDataTag();
        final boolean completelyNormal = state.transitionStage() == null
                && state.stableStage() == CompressionStage.NORMAL
                && state.requestedStage() == CompressionStage.NORMAL
                && Math.abs(state.currentScale() - 1.0D) <= PocketSized.EPSILON;

        if (completelyNormal) {
            if (userData != null && userData.contains(ROOT_KEY)) {
                userData.remove(ROOT_KEY);
                subLevel.setUserDataTag(userData);
            }
            state.markPersisted();
            return;
        }

        if (userData == null) userData = new CompoundTag();
        final CompoundTag scaleTag = new CompoundTag();
        scaleTag.putDouble(CURRENT_KEY, PocketSized.clampScale(state.currentScale()));
        scaleTag.putInt(STABLE_STAGE_KEY, state.stableStage().depth());
        scaleTag.putInt(REQUESTED_STAGE_KEY, state.requestedStage().depth());
        if (state.transitionStage() != null) {
            scaleTag.putInt(TRANSITION_STAGE_KEY, state.transitionStage().depth());
        }
        userData.put(ROOT_KEY, scaleTag);
        subLevel.setUserDataTag(userData);
        state.markPersisted();
    }

    public static void clear(final ServerSubLevel subLevel) {
        if (subLevel == null) return;
        final CompoundTag userData = subLevel.getUserDataTag();
        if (userData == null || !userData.contains(ROOT_KEY)) return;
        userData.remove(ROOT_KEY);
        subLevel.setUserDataTag(userData);
    }

    public static void restore(final ServerSubLevel subLevel, final BoundingBox3dc serializedBounds) {
        if (subLevel == null || subLevel.isRemoved()) return;
        final CompoundTag userData = subLevel.getUserDataTag();
        if (userData == null || !userData.contains(ROOT_KEY, Tag.TAG_COMPOUND)) return;

        final CompoundTag tag = userData.getCompound(ROOT_KEY);
        if (!tag.contains(CURRENT_KEY, Tag.TAG_ANY_NUMERIC)) return;

        final double current = PocketSized.clampScale(tag.getDouble(CURRENT_KEY));
        final CompressionStage stable = tag.contains(STABLE_STAGE_KEY, Tag.TAG_ANY_NUMERIC)
                ? CompressionStage.fromDepth(tag.getInt(STABLE_STAGE_KEY))
                : CompressionStage.nearest(current);
        final CompressionStage requested = tag.contains(REQUESTED_STAGE_KEY, Tag.TAG_ANY_NUMERIC)
                ? CompressionStage.fromDepth(tag.getInt(REQUESTED_STAGE_KEY))
                : tag.contains(LEGACY_TARGET_KEY, Tag.TAG_ANY_NUMERIC)
                    ? CompressionStage.nearest(tag.getDouble(LEGACY_TARGET_KEY))
                    : stable;
        final CompressionStage transition = tag.contains(TRANSITION_STAGE_KEY, Tag.TAG_ANY_NUMERIC)
                ? CompressionStage.fromDepth(tag.getInt(TRANSITION_STAGE_KEY))
                : null;

        ScaleState.restoreServerState(subLevel, current, stable, requested, transition);
        subLevel.logicalPose().scale().set(current, current, current);
        subLevel.updateBoundingBox();

        final PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(subLevel.getLevel()).getPipeline();

        if (serializedBounds != null) {
            final double correctionY = serializedBounds.minY() - subLevel.boundingBox().minY();
            if (Double.isFinite(correctionY) && Math.abs(correctionY) > 1.0E-9D && Math.abs(correctionY) < 1.0D) {
                final Vector3d corrected = new Vector3d(subLevel.logicalPose().position()).add(0.0D, correctionY, 0.0D);
                pipeline.teleport(subLevel, corrected, subLevel.logicalPose().orientation());
                subLevel.updateBoundingBox();
            }
        }

        PocketTrace.enter("PhysicsPipeline.onStatsChanged(restore)",
                "current=" + current,
                "stable=" + stable,
                "requested=" + requested,
                "transition=" + transition,
                PocketTrace.context(subLevel));
        pipeline.onStatsChanged(subLevel);
        PocketTrace.exit("PhysicsPipeline.onStatsChanged(restore) uuid=" + subLevel.getUniqueId());
    }

    private ScalePersistence() {}
}
