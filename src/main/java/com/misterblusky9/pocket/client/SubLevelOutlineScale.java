package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import org.joml.Matrix4fc;

public final class SubLevelOutlineScale {
    private static final double MATCH_TOLERANCE = 1.0E-3D;

    private static double lastPushed = 1.0D;

    public static void notePush(final double scale) {
        if (Double.isFinite(scale) && scale > 0.0D) lastPushed = scale;
    }

    public static double factor(final Matrix4fc matrix) {
        if (matrix == null) return 1.0D;

        final double scale = Math.sqrt(
                (double) matrix.m00() * matrix.m00()
                        + (double) matrix.m01() * matrix.m01()
                        + (double) matrix.m02() * matrix.m02());
        if (!Double.isFinite(scale)) return 1.0D;

        if (scale <= 1.0D + PocketSized.EPSILON) return 1.0D;

        if (Math.abs(scale - lastPushed) > lastPushed * MATCH_TOLERANCE) return 1.0D;

        return scale;
    }

    public static float normalize(final float value, final Matrix4fc matrix) {
        return (float) (value / factor(matrix));
    }

    public static float scaleDivisor(final float divisor, final Matrix4fc matrix) {
        return (float) (divisor * factor(matrix));
    }

    private SubLevelOutlineScale() {}
}
