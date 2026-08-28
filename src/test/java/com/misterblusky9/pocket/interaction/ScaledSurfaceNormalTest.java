package com.misterblusky9.pocket.interaction;

import com.misterblusky9.pocket.scale.CompressionStage;

public final class ScaledSurfaceNormalTest {
    private static final double TOLERANCE = 1.0E-9D;

    public static void main(final String[] args) {
        theSubLevelScaleIsReadBackFromTheNormal();
        everyStageAmplifiesByTheInverseScale();
        unscaledNormalsAreLeftAlone();
        degenerateAndNonFiniteNormalsPassThrough();
        directionSurvivesRescaling();
        System.out.println("ScaledSurfaceNormalTest: PASS (" + CompressionStage.values().length + " stages)");
    }

    private static void theSubLevelScaleIsReadBackFromTheNormal() {
        for (final CompressionStage stage : CompressionStage.values()) {
            final double scale = stage.scale();
            check(Math.abs(ScaledSurfaceNormal.subLevelScaleOf(0.0D, scale, 0.0D) - scale) <= TOLERANCE,
                    "stage " + stage.label() + " did not report its own scale");
        }

        check(ScaledSurfaceNormal.subLevelScaleOf(0.0D, 0.0D, 0.0D) == 1.0D,
                "a degenerate normal did not fall back to unit scale");
        check(ScaledSurfaceNormal.subLevelScaleOf(Double.NaN, 0.0D, 0.0D) == 1.0D,
                "a NaN normal did not fall back to unit scale");
    }

    private static void everyStageAmplifiesByTheInverseScale() {
        for (final CompressionStage stage : CompressionStage.values()) {
            final double scale = stage.scale();
            final double expected = 1.0D / scale;

            for (final double[] axis : axes()) {
                final double[] scaled = { axis[0] * scale, axis[1] * scale, axis[2] * scale };
                check(ScaledSurfaceNormal.needsRescale(scaled[0], scaled[1], scaled[2]) == (scale != 1.0D),
                        "stage " + stage.label() + " misjudged whether the normal needed rescaling");

                final double[] result = ScaledSurfaceNormal.perWorldBlock(scaled[0], scaled[1], scaled[2]);
                check(Math.abs(length(result) - expected) <= TOLERANCE,
                        "stage " + stage.label() + " produced length " + length(result) + ", wanted " + expected);

                for (int i = 0; i < 3; i++) {
                    check(Math.abs(result[i] - axis[i] * expected) <= TOLERANCE,
                            "stage " + stage.label() + " moved the normal off its axis");
                }
            }
        }
    }

    private static void unscaledNormalsAreLeftAlone() {
        for (final double[] axis : axes()) {
            check(!ScaledSurfaceNormal.needsRescale(axis[0], axis[1], axis[2]),
                    "an unscaled sublevel normal was flagged for rescaling");
        }

        final double[] diagonal = { 0.6D, 0.8D, 0.0D };
        check(!ScaledSurfaceNormal.needsRescale(diagonal[0], diagonal[1], diagonal[2]),
                "a unit diagonal was flagged for rescaling");
    }

    private static void degenerateAndNonFiniteNormalsPassThrough() {
        check(!ScaledSurfaceNormal.needsRescale(0.0D, 0.0D, 0.0D), "a zero normal was rescaled");
        check(!ScaledSurfaceNormal.needsRescale(Double.NaN, 0.0D, 0.0D), "a NaN normal was rescaled");
        check(!ScaledSurfaceNormal.needsRescale(Double.POSITIVE_INFINITY, 0.0D, 0.0D),
                "an infinite normal was rescaled");

        final double[] zero = ScaledSurfaceNormal.perWorldBlock(0.0D, 0.0D, 0.0D);
        check(zero[0] == 0.0D && zero[1] == 0.0D && zero[2] == 0.0D, "a zero normal was not passed through");
    }

    private static void directionSurvivesRescaling() {
        final double scale = CompressionStage.SIXTEENTH.scale();
        final double expected = 1.0D / scale;
        final double[] result = ScaledSurfaceNormal.perWorldBlock(-0.6D * scale, 0.0D, 0.8D * scale);

        check(Math.abs(result[0] + 0.6D * expected) <= TOLERANCE
                        && Math.abs(result[2] - 0.8D * expected) <= TOLERANCE,
                "rescaling flipped or skewed a diagonal normal");
        check(Math.abs(length(result) - expected) <= TOLERANCE,
                "a diagonal normal did not come back at the inverse scale");
    }

    private static double[][] axes() {
        return new double[][] {
                { 1.0D, 0.0D, 0.0D },
                { -1.0D, 0.0D, 0.0D },
                { 0.0D, 1.0D, 0.0D },
                { 0.0D, -1.0D, 0.0D },
                { 0.0D, 0.0D, 1.0D },
                { 0.0D, 0.0D, -1.0D }
        };
    }

    private static double length(final double[] vector) {
        return Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ScaledSurfaceNormalTest() {}
}
