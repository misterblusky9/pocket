package com.misterblusky9.pocket.pocket;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

public final class DeploymentClearance {
    private static final double STEP = 0.125D;

    private static final double MAX_LIFT = 4.0D;

    private static final double[] SIDEWAYS = {0.0D, 0.25D, -0.25D, 0.5D, -0.5D};

    private static final double FALLBACK_LIFT = MAX_LIFT + 1.0D;

    public static Vector3d resolve(
            final ServerLevel level,
            final Vector3d desired,
            final Vector3d extentMin,
            final Vector3d extentMax
    ) {
        if (level == null) return desired;
        if (isClear(level, boxAt(desired, extentMin, extentMax))) return desired;

        final Vector3d candidate = new Vector3d();
        for (double lift = STEP; lift <= MAX_LIFT; lift += STEP) {
            for (final double sideways : SIDEWAYS) {
                candidate.set(desired.x + sideways, desired.y + lift, desired.z);
                if (isClear(level, boxAt(candidate, extentMin, extentMax))) return new Vector3d(candidate);

                if (sideways == 0.0D) continue;
                candidate.set(desired.x, desired.y + lift, desired.z + sideways);
                if (isClear(level, boxAt(candidate, extentMin, extentMax))) return new Vector3d(candidate);
            }
        }

        return new Vector3d(desired.x, desired.y + FALLBACK_LIFT, desired.z);
    }

    private static AABB boxAt(final Vector3d position, final Vector3d extentMin, final Vector3d extentMax) {
        return new AABB(
                position.x + extentMin.x, position.y + extentMin.y, position.z + extentMin.z,
                position.x + extentMax.x, position.y + extentMax.y, position.z + extentMax.z);
    }

    private static boolean isClear(final ServerLevel level, final AABB box) {
        if (level.getBlockCollisions(null, box).iterator().hasNext()) return false;

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return true;

        for (final ServerSubLevel other : container.getAllSubLevels()) {
            if (other.isRemoved()) continue;
            final var bounds = other.boundingBox();
            if (bounds == null) continue;
            if (box.maxX > bounds.minX() && box.minX < bounds.maxX()
                    && box.maxY > bounds.minY() && box.minY < bounds.maxY()
                    && box.maxZ > bounds.minZ() && box.minZ < bounds.maxZ()) {
                return false;
            }
        }
        return true;
    }

    private DeploymentClearance() {}
}
