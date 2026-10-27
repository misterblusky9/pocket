package com.misterblusky9.pym.internal.collision;

/**
 * Shape-relative tolerance for Sable's local-space interior-contact rejection.
 * The comparison is done in (unscaled) sublevel voxel coordinates, not world
 * coordinates, so shrinking it by Pehkui/sublevel scale would be incorrect.
 */
public final class InteriorCollisionTolerance {
    /** Preserve the original 0.01 for a 1-volume cube; use 1% for smaller parts. */
    public static double forShape(double originalTolerance, double referenceVolume) {
        if (!Double.isFinite(originalTolerance) || originalTolerance <= 0.0
                || !Double.isFinite(referenceVolume) || referenceVolume < 0.0) {
            return originalTolerance;
        }
        return originalTolerance * Math.min(1.0, referenceVolume);
    }

    private InteriorCollisionTolerance() {}
}
