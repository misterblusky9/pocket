package com.misterblusky9.pym.internal.compat.simulatedcoasters;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CoasterLinkScale {
    public static double pairScale(final SubLevel a, final SubLevel b, final Double partialTick) {
        if (a == null && b == null) return 1.0D;
        if (a == null) return sanitize(CoasterScaleLookup.scaleOf(b, partialTick));
        if (b == null) return sanitize(CoasterScaleLookup.scaleOf(a, partialTick));

        final double scaleA = sanitize(CoasterScaleLookup.scaleOf(a, partialTick));
        final double scaleB = sanitize(CoasterScaleLookup.scaleOf(b, partialTick));
        if (Math.abs(scaleA - scaleB) <= ScaleBounds.EPSILON) return scaleA;

        return sanitize((scaleA + scaleB) * 0.5D);
    }

    public static double minScale(final SubLevel a, final SubLevel b, final Double partialTick) {
        if (a == null && b == null) return 1.0D;
        if (a == null) return sanitize(CoasterScaleLookup.scaleOf(b, partialTick));
        if (b == null) return sanitize(CoasterScaleLookup.scaleOf(a, partialTick));

        return Math.min(
                sanitize(CoasterScaleLookup.scaleOf(a, partialTick)),
                sanitize(CoasterScaleLookup.scaleOf(b, partialTick)));
    }

    public static void pushRenderScale(final double scale) {
        PYM$RENDER.get().push(sanitize(scale));
    }

    public static double renderScale() {
        final Deque<Double> stack = PYM$RENDER.get();
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    public static void popRenderScale() {
        final Deque<Double> stack = PYM$RENDER.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) PYM$RENDER.remove();
    }

    public static double toNominal(final double world, final double scale) {
        if (!Double.isFinite(world)) return world;
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return world;
        return world / scale;
    }

    public static double toWorld(final double nominal, final double scale) {
        if (!Double.isFinite(nominal)) return nominal;
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return nominal;
        return nominal * scale;
    }

    private static final ThreadLocal<Deque<Double>> PYM$RENDER = ThreadLocal.withInitial(ArrayDeque::new);

    private static double sanitize(final double scale) {
        if (!ScaleBounds.isValid(scale)) return 1.0D;
        return ScaleBounds.clampValid(scale);
    }

    private CoasterLinkScale() {}
}
