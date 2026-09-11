package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;

import java.util.ArrayDeque;

public final class SimulatedCoastersCartOutlineScaleContext {
    private static final ThreadLocal<ArrayDeque<Double>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(final double scale) {
        final double clean = PocketSized.isValidScale(scale) ? PocketSized.clampScale(scale) : 1.0D;
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

    private SimulatedCoastersCartOutlineScaleContext() {}
}
