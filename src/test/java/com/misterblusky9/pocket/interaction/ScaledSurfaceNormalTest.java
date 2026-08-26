package com.misterblusky9.pocket.interaction;

import com.misterblusky9.pocket.scale.CompressionStage;

public final class ScaledSurfaceNormalTest {
    private static final double TOLERANCE = 1.0E-9D;

    public static void main(final String[] args) {
        everyStageRecoversAUnitNormal();
        unitNormalsAreLeftAlone();
        degenerateAndNonFiniteNormalsPassThrough();
        directionSurvivesRenormalization();
        System.out.println("ScaledSurfaceNormalTest: PASS (" + CompressionStage.values().length + " stages)");
    }

    private static void everyStageRecoversAUnitNormal() {
        for (final CompressionStage stage : CompressionStage.values()) {
            final double scale = stage.scale();

            for (final double[] axis : axes()) {
                final double[] scaled = { axis[0] * scale, axis[1] * scale, axis[2] * scale };
                check(ScaledSurfaceNormal.needsUnit(scaled[0], scaled[1], scaled[2]) == (scale != 1.0D),
                        "stage " + stage.label() + " misjudged whether the normal needed renormalizing");

                final double[] unit = ScaledSurfaceNormal.unit(scaled[0], scaled[1], scaled[2]);
                check(Math.abs(length(unit) - 1.0D) <= TOLERANCE,
                        "stage " + stage.label() + " left a normal of length " + length(unit));

                for (int i = 0; i < 3; i++) {
                    check(Math.abs(unit[i] - axis[i]) <= TOLERANCE,
                            "stage " + stage.label() + " moved the normal off its axis");
                }
            }
        }
    }

    private static void unitNormalsAreLeftAlone() {
        for (final double[] axis : axes()) {
            check(!ScaledSurfaceNormal.needsUnit(axis[0], axis[1], axis[2]),
                    "an unscaled sublevel normal was flagged for renormalizing");
        }

        final double[] diagonal = { 0.6D, 0.8D, 0.0D };
        check(!ScaledSurfaceNormal.needsUnit(diagonal[0], diagonal[1], diagonal[2]),
                "a unit diagonal was flagged for renormalizing");
    }

    private static void degenerateAndNonFiniteNormalsPassThrough() {
        check(!ScaledSurfaceNormal.needsUnit(0.0D, 0.0D, 0.0D), "a zero normal was renormalized");
        check(!ScaledSurfaceNormal.needsUnit(Double.NaN, 0.0D, 0.0D), "a NaN normal was renormalized");
        check(!ScaledSurfaceNormal.needsUnit(Double.POSITIVE_INFINITY, 0.0D, 0.0D),
                "an infinite normal was renormalized");

        final double[] zero = ScaledSurfaceNormal.unit(0.0D, 0.0D, 0.0D);
        check(zero[0] == 0.0D && zero[1] == 0.0D && zero[2] == 0.0D, "a zero normal was not passed through");
    }

    private static void directionSurvivesRenormalization() {
        final double scale = CompressionStage.SIXTEENTH.scale();
        final double[] unit = ScaledSurfaceNormal.unit(-0.6D * scale, 0.0D, 0.8D * scale);
        check(Math.abs(unit[0] + 0.6D) <= TOLERANCE && Math.abs(unit[2] - 0.8D) <= TOLERANCE,
                "renormalizing flipped or skewed a diagonal normal");
        check(Math.abs(length(unit) - 1.0D) <= TOLERANCE, "a diagonal normal did not come back unit length");
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
