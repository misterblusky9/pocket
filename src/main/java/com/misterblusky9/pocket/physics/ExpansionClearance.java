package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ExpansionClearance {
    private static final double[] MAGNITUDES = {0.25D, 0.5D, 1.0D};

    private static final double CONTACT_EPSILON = 1.0E-3D;

    private static final double MAX_SEARCH_VOLUME = 48.0D * 48.0D * 48.0D;

    public static Vector3d resolve(
            final ServerSubLevel subLevel,
            final Vector3d desired,
            final double previousScale,
            final double nextScale
    ) {
        final ServerLevel level = subLevel.getLevel();
        if (level == null) return desired;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.inBounds(desired)) return desired;

        final Vector3d original = new Vector3d(subLevel.logicalPose().position());
        try {
            final AABB box = boxAt(subLevel, desired);
            if (box == null) return desired;

            final double penetration = floorPenetration(level, box);
            if (penetration <= 0.0D) return desired;

            final double budget = ExpansionBudget.perTick(
                    desired.y, box.minY, previousScale, nextScale);
            final double lift = Math.min(penetration, budget);

            final Vector3d lifted = new Vector3d(desired).add(0.0D, lift, 0.0D);
            if (isClear(level, boxAt(subLevel, lifted))) return lifted;

            if (volume(box) <= MAX_SEARCH_VOLUME) {
                final Vector3d candidate = new Vector3d();
                for (final double magnitude : MAGNITUDES) {
                    final double step = budget * magnitude;
                    if (isClear(level, boxAt(subLevel, candidate.set(desired).add(step, 0.0D, 0.0D)))) {
                        return new Vector3d(candidate);
                    }
                    if (isClear(level, boxAt(subLevel, candidate.set(desired).add(-step, 0.0D, 0.0D)))) {
                        return new Vector3d(candidate);
                    }
                    if (isClear(level, boxAt(subLevel, candidate.set(desired).add(0.0D, 0.0D, step)))) {
                        return new Vector3d(candidate);
                    }
                    if (isClear(level, boxAt(subLevel, candidate.set(desired).add(0.0D, 0.0D, -step)))) {
                        return new Vector3d(candidate);
                    }
                }
            }

            return hasHeadroom(level, box, lift) ? lifted : desired;
        } finally {
            subLevel.logicalPose().position().set(original);
            subLevel.updateBoundingBox();
        }
    }

    private static double floorPenetration(final ServerLevel level, final AABB box) {
        double highest = Double.NEGATIVE_INFINITY;
        for (final VoxelShape shape : level.getBlockCollisions(null, box)) {
            if (shape.isEmpty()) continue;
            highest = Math.max(highest, shape.bounds().maxY);
        }
        if (highest == Double.NEGATIVE_INFINITY) return 0.0D;
        return Math.max(0.0D, highest - box.minY);
    }

    private static boolean hasHeadroom(final ServerLevel level, final AABB box, final double lift) {
        if (lift <= 0.0D) return false;
        return level.noCollision(new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + lift, box.maxZ));
    }

    private static boolean isClear(final ServerLevel level, final AABB box) {
        return box == null || level.noCollision(box);
    }

    private static double volume(final AABB box) {
        return box.getXsize() * box.getYsize() * box.getZsize();
    }

    private static AABB boxAt(final ServerSubLevel subLevel, final Vector3dc position) {
        subLevel.logicalPose().position().set(position);
        subLevel.updateBoundingBox();

        final BoundingBox3dc box = subLevel.boundingBox();
        if (box == null) return null;

        final double minX = box.minX() + CONTACT_EPSILON;
        final double minY = box.minY() + CONTACT_EPSILON;
        final double minZ = box.minZ() + CONTACT_EPSILON;
        final double maxX = box.maxX() - CONTACT_EPSILON;
        final double maxY = box.maxY() - CONTACT_EPSILON;
        final double maxZ = box.maxZ() - CONTACT_EPSILON;
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return null;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private ExpansionClearance() {}
}
