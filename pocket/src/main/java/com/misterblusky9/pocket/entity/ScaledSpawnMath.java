package com.misterblusky9.pocket.entity;

public final class ScaledSpawnMath {
    public static final double MAX_PLAYER_TO_BED = 1.5D;
    private static final double EPSILON = 1.0E-6D;

    private ScaledSpawnMath() {}

    public static boolean fits(double playerScale, double bedScale) {
        return finitePositive(playerScale) && finitePositive(bedScale)
                && playerScale <= bedScale * MAX_PLAYER_TO_BED * (1.0D + EPSILON);
    }

    public static double restore(double currentBedScale, double savedRatio) {
        if (!finitePositive(currentBedScale) || !finitePositive(savedRatio)
                || savedRatio > MAX_PLAYER_TO_BED * (1.0D + EPSILON)) return 1.0D;
        double result = currentBedScale * savedRatio;
        return finitePositive(result) ? result : 1.0D;
    }

    private static boolean finitePositive(double scale) {
        return Double.isFinite(scale) && scale > 0.0D;
    }
}
