package com.misterblusky9.pocket.client;

public final class HeadwearUnderHat {
    private static int depth;
    private static boolean polygonOffsetSuppressed;

    private HeadwearUnderHat() {}

    public static boolean active() {
        return depth > 0;
    }

    public static void run(Runnable draw) {
        depth++;
        try {
            draw.run();
        } finally {
            depth--;
        }
    }

    public static boolean polygonOffsetSuppressed() {
        return polygonOffsetSuppressed;
    }

    public static void withoutPolygonOffset(Runnable draw) {
        boolean previous = polygonOffsetSuppressed;
        polygonOffsetSuppressed = true;
        try {
            draw.run();
        } finally {
            polygonOffsetSuppressed = previous;
        }
    }
}
