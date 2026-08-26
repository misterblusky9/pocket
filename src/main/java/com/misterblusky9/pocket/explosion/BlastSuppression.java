package com.misterblusky9.pocket.explosion;

public final class BlastSuppression {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        final int[] depth = DEPTH.get();
        if (depth[0] > 0) depth[0]--;
    }

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    private BlastSuppression() {}
}
