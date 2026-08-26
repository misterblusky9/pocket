package com.misterblusky9.pocket.interaction;

import com.misterblusky9.pocket.PocketSized;

public final class ScaledSurfaceNormal {
    private static final double DEGENERATE_LENGTH = 1.0E-9D;

    public static boolean needsUnit(final double x, final double y, final double z) {
        final double lengthSquared = x * x + y * y + z * z;
        if (!Double.isFinite(lengthSquared)) return false;
        if (!(lengthSquared > DEGENERATE_LENGTH * DEGENERATE_LENGTH)) return false;

        return Math.abs(Math.sqrt(lengthSquared) - 1.0D) > PocketSized.EPSILON;
    }

    public static double[] unit(final double x, final double y, final double z) {
        if (!needsUnit(x, y, z)) return new double[] { x, y, z };

        final double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] { x / length, y / length, z / length };
    }

    private ScaledSurfaceNormal() {}
}
