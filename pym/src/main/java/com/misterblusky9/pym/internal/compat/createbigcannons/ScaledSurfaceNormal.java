package com.misterblusky9.pym.internal.compat.createbigcannons;

import com.misterblusky9.pym.api.ScaleBounds;

public final class ScaledSurfaceNormal {
    private static final double DEGENERATE_LENGTH = 1.0E-9D;

    public static double subLevelScaleOf(double x, double y, double z) {
        double lengthSquared = x * x + y * y + z * z;
        if (!Double.isFinite(lengthSquared)) return 1.0D;
        if (!(lengthSquared > DEGENERATE_LENGTH * DEGENERATE_LENGTH)) return 1.0D;

        return Math.sqrt(lengthSquared);
    }

    public static boolean needsRescale(double x, double y, double z) {
        double lengthSquared = x * x + y * y + z * z;
        if (!Double.isFinite(lengthSquared)) return false;
        if (!(lengthSquared > DEGENERATE_LENGTH * DEGENERATE_LENGTH)) return false;

        return Math.abs(Math.sqrt(lengthSquared) - 1.0D) > ScaleBounds.EPSILON;
    }

    public static double[] perWorldBlock(double x, double y, double z) {
        if (!needsRescale(x, y, z)) return new double[] { x, y, z };

        double scale = subLevelScaleOf(x, y, z);
        double factor = 1.0D / (scale * scale);
        return new double[] { x * factor, y * factor, z * factor };
    }

    private ScaledSurfaceNormal() {}
}
