package com.misterblusky9.pocket.scale;

import java.math.BigDecimal;
import java.math.MathContext;

public final class ScaleFormat {
    private static final int[] DENOMINATORS = {2, 3, 4, 8, 16, 32, 64};
    private static final double FRACTION_TOLERANCE = 1.0E-6D;
    private static final MathContext DISPLAY_PRECISION = new MathContext(3);

    public static String label(final double scale) {
        return number(scale) + "×";
    }

    public static String number(final double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return "?";
        final String fraction = fraction(scale);
        return fraction == null ? decimal(scale) : fraction;
    }

    public static String fraction(final double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return null;
        final long whole = Math.round(scale);
        if (whole >= 1L && Math.abs(scale - whole) <= FRACTION_TOLERANCE) return Long.toString(whole);
        if (scale > 1.0D) return null;
        for (final int denominator : DENOMINATORS) {
            final double numerator = scale * denominator;
            final long rounded = Math.round(numerator);
            if (rounded > 0 && Math.abs(numerator - rounded) <= FRACTION_TOLERANCE * denominator) {
                final long divisor = gcd(rounded, denominator);
                return (rounded / divisor) + "/" + (denominator / divisor);
            }
        }
        return null;
    }

    public static String decimal(final double scale) {
        return new BigDecimal(scale).round(DISPLAY_PRECISION).stripTrailingZeros().toPlainString();
    }

    private static long gcd(final long a, final long b) {
        return b == 0L ? Math.abs(a) : gcd(b, a % b);
    }

    private ScaleFormat() {}
}
