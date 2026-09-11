package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import org.joml.Matrix4fc;

public final class SubLevelOutlineScale {
    private static final double MATCH_TOLERANCE = 1.0E-3D;

    private static final ThreadLocal<Double> POCKET$LAST = ThreadLocal.withInitial(() -> 1.0D);

    public static void notePush(final double scale) {
        if (PocketSized.isValidScale(scale)) POCKET$LAST.set(PocketSized.clampScale(scale));
    }

    public static double factor(final Matrix4fc matrix) {
        if (matrix == null) return 1.0D;

        final double matrixScale = Math.sqrt(
                (double) matrix.m00() * matrix.m00()
                        + (double) matrix.m01() * matrix.m01()
                        + (double) matrix.m02() * matrix.m02());
        if (!Double.isFinite(matrixScale) || matrixScale <= 1.0D + PocketSized.EPSILON) return 1.0D;

        final double subLevelScale = POCKET$LAST.get();
        if (!PocketSized.isValidScale(subLevelScale) || subLevelScale >= 1.0D - PocketSized.EPSILON) return 1.0D;

        final double expected = 1.0D / subLevelScale;
        if (Math.abs(matrixScale - expected) > expected * MATCH_TOLERANCE) return 1.0D;

        return matrixScale;
    }

    public static float normalize(final float value, final Matrix4fc matrix) {
        return (float) (value / factor(matrix));
    }

    public static float scaleDivisor(final float divisor, final Matrix4fc matrix) {
        return (float) (divisor * factor(matrix));
    }

    private SubLevelOutlineScale() {}
}
