package com.misterblusky9.pocket.interaction;

import com.misterblusky9.pocket.PocketSized;

public final class ScaledSurfaceNormal {
    private static final double DEGENERATE_LENGTH = 1.0E-9D;

    public static double subLevelScaleOf(final double x, final double y, final double z) {
        final double lengthSquared = x * x + y * y + z * z;
        if (!Double.isFinite(lengthSquared)) return 1.0D;
        if (!(lengthSquared > DEGENERATE_LENGTH * DEGENERATE_LENGTH)) return 1.0D;

        return Math.sqrt(lengthSquared);
    }

    public static boolean needsRescale(final double x, final double y, final double z) {
        final double lengthSquared = x * x + y * y + z * z;
        if (!Double.isFinite(lengthSquared)) return false;
        if (!(lengthSquared > DEGENERATE_LENGTH * DEGENERATE_LENGTH)) return false;

        return Math.abs(Math.sqrt(lengthSquared) - 1.0D) > PocketSized.EPSILON;
    }

    public static double[] perWorldBlock(final double x, final double y, final double z) {
        if (!needsRescale(x, y, z)) return new double[] { x, y, z };

        final double scale = subLevelScaleOf(x, y, z);
        final double factor = 1.0D / (scale * scale);
        return new double[] { x * factor, y * factor, z * factor };
    }

    private ScaledSurfaceNormal() {}
}
