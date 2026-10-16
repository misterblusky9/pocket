package com.misterblusky9.pym.internal.client;

public final class SubLevelBlockEntityPass {
    private static int depth;

    public static void begin() {
        depth++;
    }

    public static void end() {
        if (depth > 0) depth--;
    }

    public static void reset() {
        depth = 0;
    }

    public static boolean active() {
        return depth > 0;
    }

    private SubLevelBlockEntityPass() {}
}
