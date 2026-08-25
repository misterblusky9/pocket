package com.misterblusky9.pocket.physics;

public final class SubLevelLoadGuard {
    private static volatile Thread loadingThread;

    public static void beginLoad() {
        loadingThread = Thread.currentThread();
    }

    public static void endLoad() {
        loadingThread = null;
    }

    public static boolean isLoading() {
        return loadingThread == Thread.currentThread();
    }

    public static void clearIfStaleOn(final Thread thread) {
        if (loadingThread == thread) loadingThread = null;
    }

    private SubLevelLoadGuard() {}
}
