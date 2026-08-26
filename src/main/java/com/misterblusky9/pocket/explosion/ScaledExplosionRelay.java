package com.misterblusky9.pocket.explosion;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ScaledExplosionRelay {
    public static final int MAX_MIRRORED_SUBLEVELS = 4;

    public static boolean isScaled(final SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;

        final double scale = ScaleState.getScale(subLevel);
        return Double.isFinite(scale) && Math.abs(scale - 1.0D) > PocketSized.EPSILON;
    }

    public static List<SubLevel> mirrorTargets(
            final Level level,
            final double x,
            final double y,
            final double z,
            final float radius
    ) {
        if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return List.of();

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();

        if (Sable.HELPER.getContaining(level, new Vec3(x, y, z)) != null) return List.of();

        List<SubLevel> targets = null;

        for (final SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved() || !isScaled(subLevel)) continue;

            final BoundingBox3dc bounds = subLevel.boundingBox();
            if (bounds == null) continue;

            if (!BlastReach.reaches(
                    x, y, z, radius,
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()
            )) {
                continue;
            }

            if (targets == null) targets = new ArrayList<>(MAX_MIRRORED_SUBLEVELS);
            targets.add(subLevel);
            if (targets.size() >= MAX_MIRRORED_SUBLEVELS) break;
        }

        return targets == null ? List.of() : targets;
    }

    public static Vec3 plotCenter(
            final Level level,
            final SubLevel subLevel,
            final double x,
            final double y,
            final double z
    ) {
        final Pose3dc pose = level instanceof final LevelPoseProviderExtension extension
                ? extension.sable$getPose(subLevel)
                : subLevel.logicalPose();

        final Vec3 center = pose.transformPositionInverse(new Vec3(x, y, z));
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return null;

        return center;
    }

    private ScaledExplosionRelay() {}
}
