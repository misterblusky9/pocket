package com.misterblusky9.pym.api;

public record ScaleBounds(double min, double max) {
    public static final double FULL = 1.0D;
    public static final double EPSILON = 1.0E-6D;

    public static final ScaleBounds SAFE = new ScaleBounds(1.0D / 16.0D, 8.0D);

    public static final ScaleBounds ANY = new ScaleBounds(Double.MIN_VALUE, Double.MAX_VALUE);

    @Deprecated(forRemoval = false)
    public static final ScaleBounds EXPERIMENTAL = ANY;

    public ScaleBounds {
        if (!(min > 0.0D) || !(max >= min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("Invalid scale bounds " + min + ".." + max);
        }
    }

    public double clamp(final double scale) {
        return Math.max(this.min, Math.min(this.max, scale));
    }

    public boolean contains(final double scale) {
        return isValid(scale) && (scale > this.min || same(scale, this.min))
                && (scale < this.max || same(scale, this.max));
    }

    public boolean permits(final double from, final double to) {
        if (!isValid(to)) return false;
        final boolean aboveFloor = to > this.min || same(to, this.min)
                || to > from || same(to, from);
        final boolean belowCeiling = to < this.max || same(to, this.max)
                || to < from || same(to, from);
        return aboveFloor && belowCeiling;
    }

    public static boolean isValid(final double scale) {
        return Double.isFinite(scale) && scale > 0.0D;
    }

    public static double clampValid(final double scale) {
        if (Double.isNaN(scale)) return Double.NaN;
        if (isValid(scale)) return scale;
        return FULL;
    }

    public static boolean same(final double a, final double b) {
        if (Double.doubleToLongBits(a) == Double.doubleToLongBits(b)) return true;
        if (!Double.isFinite(a) || !Double.isFinite(b)) return false;
        final double scale = Math.max(Math.abs(a), Math.abs(b));
        return scale > 0.0D && Math.abs(a - b) <= EPSILON * scale;
    }
}
