package com.misterblusky9.pocket.compat.create;

public final class ContraptionCollisionScaleContext {

    private static final ThreadLocal<Double> SCALE =
            ThreadLocal.withInitial(() -> 1.0D);

    private ContraptionCollisionScaleContext() {
    }

    public static double currentScale() {
        return SCALE.get();
    }

    public static double swap(final double scale) {
        final double previous = SCALE.get();
        SCALE.set(scale);
        return previous;
    }

    public static void restore(final double previousScale) {
        if (Math.abs(previousScale - 1.0D) <= 1.0E-9D) {
            SCALE.remove();
        } else {
            SCALE.set(previousScale);
        }
    }
}