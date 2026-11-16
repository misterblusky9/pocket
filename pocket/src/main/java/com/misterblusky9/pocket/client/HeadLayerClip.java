package com.misterblusky9.pocket.client;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class HeadLayerClip {
    public static final double SKIN_LAYER = 4.5;
    public static final double HEADWEAR = 5.0;
    public static final double HELMET = 5.5;
    private static final double MARGIN = 0.25;

    private static @Nullable Object head;
    private static double reach;

    private HeadLayerClip() {}

    public static void begin(Object owner, Vector3d headPixels) {
        head = owner;
        reach = Math.max(Math.abs(headPixels.x), Math.max(Math.abs(headPixels.y), Math.abs(headPixels.z)));
    }

    public static void end() {
        head = null;
    }

    public static boolean hides(Object owner, double halfPixels) {
        return head != null && owner == head && reach <= halfPixels + MARGIN;
    }
}
