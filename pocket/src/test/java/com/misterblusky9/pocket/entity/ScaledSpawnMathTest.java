package com.misterblusky9.pocket.entity;

public final class ScaledSpawnMathTest {
    public static void main(String[] args) {
        check(ScaledSpawnMath.fits(0.125, 0.125), "tiny player/tiny bed");
        check(ScaledSpawnMath.fits(0.0625, 0.125), "smaller player/larger bed");
        check(ScaledSpawnMath.fits(1.5, 1.0), "1.5x cutoff inclusive");
        check(!ScaledSpawnMath.fits(1.5001, 1.0), "above cutoff rejected");
        check(!ScaledSpawnMath.fits(2.0, 1.0), "2x player/1x bed rejected");
        check(ScaledSpawnMath.fits(1.0, 4.0), "large beds allow small players");
        check(ScaledSpawnMath.fits(1.0, 8.0), "normal player/eightfold bed");
        check(ScaledSpawnMath.fits(8.0, 8.0), "eightfold player/eightfold bed");
        equal(0.125, ScaledSpawnMath.restore(0.125, 1.0), "tiny bed unchanged");
        equal(2.0, ScaledSpawnMath.restore(2.0, 1.0), "grown bed");
        equal(8.0, ScaledSpawnMath.restore(8.0, 1.0), "eightfold player/eightfold bed");
        equal(1.0, ScaledSpawnMath.restore(8.0, 0.125), "normal player/eightfold bed");
        equal(0.0625, ScaledSpawnMath.restore(0.125, 0.5), "relative half-size");
        equal(1.0, ScaledSpawnMath.restore(1.0, 2.0), "invalid ratio fails safe");
        equal(1.0, ScaledSpawnMath.restore(0.0, 1.0), "missing size fails safe");
        equal(1.0, ScaledSpawnMath.restore(Double.NaN, 1.0), "NaN fails safe");
        equal(1.0, ScaledSpawnMath.restore(Double.POSITIVE_INFINITY, 1.0), "infinity fails safe");
        System.out.println("17 scaled spawn math checks passed");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    private static void equal(double expected, double actual, String name) {
        if (Math.abs(expected - actual) > 1.0E-8D) throw new AssertionError(name + ": " + actual);
    }
}
