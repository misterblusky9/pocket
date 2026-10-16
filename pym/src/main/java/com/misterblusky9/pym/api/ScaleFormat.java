package com.misterblusky9.pym.api;

import java.math.BigDecimal;
import java.math.MathContext;

public final class ScaleFormat {
    private static final int[] DENOMINATORS = {2, 3, 4, 8, 16, 32, 64, 128, 256};
    private static final double FRACTION_TOLERANCE = 1.0E-6D;
    private static final MathContext DISPLAY_PRECISION = new MathContext(3);
    private static final long PLAIN_UNIT_DENOMINATOR = 16L;
    private static final long MAX_UNIT_DENOMINATOR = 10_000L;

    public static String label(double scale) {
        return number(scale) + "×";
    }

    public static String number(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return "?";
        String fraction = fraction(scale);
        return fraction == null ? decimal(scale) : fraction;
    }

    public static String fraction(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return null;
        long whole = Math.round(scale);
        if (whole >= 1L && Math.abs(scale - whole) <= FRACTION_TOLERANCE) return Long.toString(whole);
        if (scale > 1.0D) return null;
        for (int denominator : DENOMINATORS) {
            double numerator = scale * denominator;
            long rounded = Math.round(numerator);
            if (rounded > 0 && Math.abs(numerator - rounded) <= FRACTION_TOLERANCE * denominator) {
                long divisor = gcd(rounded, denominator);
                return (rounded / divisor) + "/" + (denominator / divisor);
            }
        }
        long unit = unitDenominator(scale, FRACTION_TOLERANCE);
        return unit > 0L ? "1/" + unit : null;
    }

    public static long unitDenominator(double scale, double tolerance) {
        if (!Double.isFinite(scale) || scale <= 0.0D || scale >= 1.0D) return 0L;
        long n = Math.round(1.0D / scale);
        if (n < 2L || n > MAX_UNIT_DENOMINATOR || Math.abs(scale * n - 1.0D) > tolerance) return 0L;
        return n <= PLAIN_UNIT_DENOMINATOR || smooth(n) ? n : 0L;
    }

    private static boolean smooth(long n) {
        for (long prime : new long[] {2L, 3L, 5L}) {
            while (n % prime == 0L) n /= prime;
        }
        return n == 1L;
    }

    public static String decimal(double scale) {
        return new BigDecimal(scale).round(DISPLAY_PRECISION).stripTrailingZeros().toPlainString();
    }

    private static long gcd(long a, long b) {
        return b == 0L ? Math.abs(a) : gcd(b, a % b);
    }

    private ScaleFormat() {}
}
