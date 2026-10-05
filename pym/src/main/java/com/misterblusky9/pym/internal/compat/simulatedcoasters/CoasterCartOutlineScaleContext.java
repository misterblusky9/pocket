package com.misterblusky9.pym.internal.compat.simulatedcoasters;

import com.misterblusky9.pym.api.ScaleBounds;

import java.util.ArrayDeque;

public final class CoasterCartOutlineScaleContext {
    private static final ThreadLocal<ArrayDeque<Double>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(final double scale) {
        final double clean = ScaleBounds.isValid(scale) ? ScaleBounds.clampValid(scale) : 1.0D;
        STACK.get().push(clean);
    }

    public static void pop() {
        final ArrayDeque<Double> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    public static double current() {
        final ArrayDeque<Double> stack = STACK.get();
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    private CoasterCartOutlineScaleContext() {}
}
