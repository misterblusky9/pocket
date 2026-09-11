package com.misterblusky9.pocket.physics;

public final class SubLevelLoadGuard {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void beginLoad() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void endLoad() {
        final int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
            return;
        }
        DEPTH.set(depth - 1);
    }

    public static boolean isLoading() {
        return DEPTH.get() > 0;
    }

    private SubLevelLoadGuard() {}
}
