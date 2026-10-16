package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector3d;

public final class ScalePersistence {
    public static final String ROOT_KEY = "pym_scale";
    public static final String LEGACY_ROOT_KEY = "pocket_scale";
    private static final String CURRENT_KEY = "current";
    private static final String TARGET_KEY = "target";
    private static final String LEGACY_REQUESTED_KEY = "requested";
    private static final String LEGACY_STABLE_KEY = "stable";
    private static final String LEGACY_STABLE_STAGE_KEY = "stable_stage";
    private static final String LEGACY_REQUESTED_STAGE_KEY = "requested_stage";
    private static final String LEGACY_TARGET_KEY = "manual_target";
    private static final int LEGACY_DEEPEST_STAGE = 4;

    public static void persist(ServerSubLevel subLevel) {
        ScaleRecord record = subLevel == null ? null : ScaleState.recordOf(subLevel.getUniqueId());
        if (record == null) return;

        CompoundTag userData = subLevel.getUserDataTag() == null ? new CompoundTag() : subLevel.getUserDataTag();
        userData.remove(LEGACY_ROOT_KEY);
        if (!record.moving() && ScaleBounds.same(record.current(), ScaleBounds.FULL)) {
            userData.remove(ROOT_KEY);
        } else {
            CompoundTag scale = new CompoundTag();
            scale.putDouble(CURRENT_KEY, record.current());
            scale.putDouble(TARGET_KEY, record.target());
            userData.put(ROOT_KEY, scale);
        }
        subLevel.setUserDataTag(userData);
        record.markPersisted();
    }

    public static void restore(ServerSubLevel subLevel, BoundingBox3dc savedBounds) {
        if (subLevel == null || subLevel.isRemoved()) return;
        CompoundTag tag = storedTag(subLevel.getUserDataTag());
        if (tag == null || !tag.contains(CURRENT_KEY, Tag.TAG_ANY_NUMERIC)) return;

        double current = tag.getDouble(CURRENT_KEY);
        if (!ScaleBounds.isValid(current)) return;
        double target = savedTarget(tag, current);

        ScaleRecord record = ScaleState.settle(subLevel, current);
        if (!ScaleBounds.same(current, target)) {
            record.begin(target, Resizer.DEFAULT_TICKS, Pivot.on(subLevel, subLevel.logicalPose().rotationPoint()), null, true);
        }
        ScaleMotion.setPoseScale(subLevel, current);

        PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(subLevel.getLevel()).getPipeline();
        if (savedBounds != null) {
            double drop = savedBounds.minY() - subLevel.boundingBox().minY();
            if (Double.isFinite(drop) && Math.abs(drop) > 1.0E-9D && Math.abs(drop) < 1.0D) {
                Vector3d corrected = new Vector3d(subLevel.logicalPose().position()).add(0.0D, drop, 0.0D);
                pipeline.teleport(subLevel, corrected, subLevel.logicalPose().orientation());
                subLevel.updateBoundingBox();
            }
        }
        pipeline.onStatsChanged(subLevel);
    }

    public static CompoundTag storedTag(CompoundTag userData) {
        if (userData == null) return null;
        if (userData.contains(ROOT_KEY, Tag.TAG_COMPOUND)) return userData.getCompound(ROOT_KEY);
        if (userData.contains(LEGACY_ROOT_KEY, Tag.TAG_COMPOUND)) return userData.getCompound(LEGACY_ROOT_KEY);
        return null;
    }

    static double savedTarget(CompoundTag tag, double current) {
        for (String key : new String[] {TARGET_KEY, LEGACY_REQUESTED_KEY}) {
            if (tag.contains(key, Tag.TAG_ANY_NUMERIC) && ScaleBounds.isValid(tag.getDouble(key))) {
                return tag.getDouble(key);
            }
        }
        if (tag.contains(LEGACY_REQUESTED_STAGE_KEY, Tag.TAG_ANY_NUMERIC)) {
            return legacyStageScale(tag.getInt(LEGACY_REQUESTED_STAGE_KEY));
        }
        if (tag.contains(LEGACY_TARGET_KEY, Tag.TAG_ANY_NUMERIC)) return nearestLegacyStage(tag.getDouble(LEGACY_TARGET_KEY));
        if (tag.contains(LEGACY_STABLE_KEY, Tag.TAG_ANY_NUMERIC) && ScaleBounds.isValid(tag.getDouble(LEGACY_STABLE_KEY))) {
            return tag.getDouble(LEGACY_STABLE_KEY);
        }
        if (tag.contains(LEGACY_STABLE_STAGE_KEY, Tag.TAG_ANY_NUMERIC)) {
            return legacyStageScale(tag.getInt(LEGACY_STABLE_STAGE_KEY));
        }
        return current;
    }

    static double legacyStageScale(int depth) {
        return Math.scalb(1.0D, -Math.max(0, Math.min(LEGACY_DEEPEST_STAGE, depth)));
    }

    static double nearestLegacyStage(double scale) {
        double best = 1.0D;
        for (int depth = 0; depth <= LEGACY_DEEPEST_STAGE; depth++) {
            double stage = legacyStageScale(depth);
            if (Math.abs(stage - scale) < Math.abs(best - scale)) best = stage;
        }
        return best;
    }

    private ScalePersistence() {}
}
