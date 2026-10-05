package com.misterblusky9.pym.internal.compat.simulatedcoasters;

import com.misterblusky9.pym.api.ScaleBounds;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CoasterCartScaleContext {
    private static final ThreadLocal<Deque<Double>> PYM$STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(final double scale) {
        PYM$STACK.get().push(sanitize(scale));
    }

    public static double current() {
        final Deque<Double> stack = PYM$STACK.get();
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    public static void pop() {
        final Deque<Double> stack = PYM$STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) PYM$STACK.remove();
    }

    private static double sanitize(final double scale) {
        if (!ScaleBounds.isValid(scale)) return 1.0D;
        return ScaleBounds.clampValid(scale);
    }

    private CoasterCartScaleContext() {}
}
