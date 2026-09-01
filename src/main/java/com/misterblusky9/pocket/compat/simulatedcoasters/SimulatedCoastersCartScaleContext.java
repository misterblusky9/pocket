package com.misterblusky9.pocket.compat.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;

import java.util.ArrayDeque;
import java.util.Deque;

public final class SimulatedCoastersCartScaleContext {
    private static final ThreadLocal<Deque<Double>> POCKET$STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(final double scale) {
        POCKET$STACK.get().push(sanitize(scale));
    }

    public static double current() {
        final Deque<Double> stack = POCKET$STACK.get();
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    public static void pop() {
        final Deque<Double> stack = POCKET$STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) POCKET$STACK.remove();
    }

    private static double sanitize(final double scale) {
        if (!PocketSized.isValidScale(scale)) return 1.0D;
        return PocketSized.clampScale(scale);
    }

    private SimulatedCoastersCartScaleContext() {}
}
