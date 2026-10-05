package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;

public final class ScaleSnap {
    private static final double TOLERANCE = 1.0E-6D;
    private static final int MIN_EXPONENT = -6;
    private static final int MAX_EXPONENT = 6;

    public static double snap(final double scale) {
        final double clamped = ScaleBounds.clampValid(scale);
        for (int exponent = MIN_EXPONENT; exponent <= MAX_EXPONENT; exponent++) {
            final double power = Math.scalb(1.0D, exponent);
            if (Math.abs(power - clamped) <= TOLERANCE) return power;
        }
        return clamped;
    }

    private ScaleSnap() {}
}
