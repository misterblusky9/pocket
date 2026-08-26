package com.misterblusky9.pocket.explosion;

public final class BlastReach {
    public static final double AIR_REACH_FACTOR = 1.75D;

    public static boolean reaches(
            final double centerX,
            final double centerY,
            final double centerZ,
            final double radius,
            final double minX,
            final double minY,
            final double minZ,
            final double maxX,
            final double maxY,
            final double maxZ
    ) {
        if (!(radius > 0.0D) || !Double.isFinite(radius)) return false;
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)) return false;
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)) return false;
        if (!Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) return false;

        final double reach = radius * AIR_REACH_FACTOR;

        final double deltaX = axisGap(centerX, minX, maxX);
        final double deltaY = axisGap(centerY, minY, maxY);
        final double deltaZ = axisGap(centerZ, minZ, maxZ);

        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= reach * reach;
    }

    private static double axisGap(final double value, final double min, final double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0.0D;
    }

    private BlastReach() {}
}
