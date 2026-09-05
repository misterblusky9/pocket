package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.ArrayDeque;
import java.util.Deque;

public final class SimulatedCoastersLinkScale {
    public static double pairScale(final SubLevel a, final SubLevel b, final Double partialTick) {
        if (a == null && b == null) return 1.0D;
        if (a == null) return sanitize(SimulatedCoastersScaleLookup.scaleOf(b, partialTick));
        if (b == null) return sanitize(SimulatedCoastersScaleLookup.scaleOf(a, partialTick));

        final double scaleA = sanitize(SimulatedCoastersScaleLookup.scaleOf(a, partialTick));
        final double scaleB = sanitize(SimulatedCoastersScaleLookup.scaleOf(b, partialTick));
        if (Math.abs(scaleA - scaleB) <= PocketSized.EPSILON) return scaleA;

        return sanitize((scaleA + scaleB) * 0.5D);
    }

    public static double minScale(final SubLevel a, final SubLevel b, final Double partialTick) {
        if (a == null && b == null) return 1.0D;
        if (a == null) return sanitize(SimulatedCoastersScaleLookup.scaleOf(b, partialTick));
        if (b == null) return sanitize(SimulatedCoastersScaleLookup.scaleOf(a, partialTick));

        return Math.min(
                sanitize(SimulatedCoastersScaleLookup.scaleOf(a, partialTick)),
                sanitize(SimulatedCoastersScaleLookup.scaleOf(b, partialTick)));
    }

    public static void pushRenderScale(final double scale) {
        POCKET$RENDER.get().push(sanitize(scale));
    }

    public static double renderScale() {
        final Deque<Double> stack = POCKET$RENDER.get();
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    public static void popRenderScale() {
        final Deque<Double> stack = POCKET$RENDER.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) POCKET$RENDER.remove();
    }

    public static double toNominal(final double world, final double scale) {
        if (!Double.isFinite(world)) return world;
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return world;
        return world / scale;
    }

    public static double toWorld(final double nominal, final double scale) {
        if (!Double.isFinite(nominal)) return nominal;
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return nominal;
        return nominal * scale;
    }

    private static final ThreadLocal<Deque<Double>> POCKET$RENDER = ThreadLocal.withInitial(ArrayDeque::new);

    private static double sanitize(final double scale) {
        if (!PocketSized.isValidScale(scale)) return 1.0D;
        return PocketSized.clampScale(scale);
    }

    private SimulatedCoastersLinkScale() {}
}
