package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;

public final class ScaleTransitionCurve {
    public static final double DEFAULT_TICKS = 9.0D;

    private ScaleTransitionCurve() {}

    public static double interpolate(
            final double from,
            final double to,
            final int elapsedTicks,
            final double speedFactor
    ) {
        final double ticks = durationTicks(speedFactor);
        final double progress = Math.min(1.0D, Math.max(0.0D, elapsedTicks / Math.max(1.0D, ticks)));
        if (progress >= 1.0D) return PocketSized.clampScale(to);

        final double eased = 1.0D - (1.0D - progress) * (1.0D - progress);
        return PocketSized.clampScale(from + (to - from) * eased);
    }

    public static boolean complete(final int elapsedTicks, final double speedFactor) {
        return elapsedTicks >= Math.max(1.0D, durationTicks(speedFactor));
    }

    private static double durationTicks(final double effectiveSpeedFactor) {
        final double speed = Double.isFinite(effectiveSpeedFactor) && effectiveSpeedFactor > 0.0D
                ? effectiveSpeedFactor
                : 1.0D;
        return DEFAULT_TICKS / Math.max(0.05D, speed);
    }

    static double sanitizeSpeedFactor(final double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0D) return 1.0D;
        return Math.min(4.0D, factor);
    }
}
