package com.misterblusky9.pym.internal.collision;

public final class InteriorCollisionTolerance {
    public static double forShape(double originalTolerance, double referenceVolume) {
        if (!Double.isFinite(originalTolerance) || originalTolerance <= 0.0
                || !Double.isFinite(referenceVolume) || referenceVolume < 0.0) {
            return originalTolerance;
        }
        return originalTolerance * Math.min(1.0, referenceVolume);
    }

    private InteriorCollisionTolerance() {}
}
