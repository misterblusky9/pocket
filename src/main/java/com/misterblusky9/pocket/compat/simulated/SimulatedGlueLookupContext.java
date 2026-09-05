package com.misterblusky9.pocket.compat.simulated;

public final class SimulatedGlueLookupContext {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    public static void enter() {
        final Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void exit() {
        final Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) {
            DEPTH.remove();
            return;
        }
        DEPTH.set(depth - 1);
    }

    public static boolean active() {
        final Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    private SimulatedGlueLookupContext() {}
}
