package com.misterblusky9.pym.internal.client;

public final class ParticleScalePolicy {
    public static boolean valid(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    public static double emitterScale(double explicitScale, double originScale) {
        return valid(explicitScale) ? explicitScale : valid(originScale) ? originScale : 1.0D;
    }

    public static double remainingVelocityRatio(double emitterScale, double projectionScale) {
        return emitterScale / (valid(projectionScale) ? projectionScale : 1.0D);
    }

    public static double constructorWorldLiftAdjustment(double plotScale) {
        return valid(plotScale) ? 0.1D * (plotScale - 1.0D) : 0.0D;
    }

    public static double terrainLegacyWorldLiftAdjustment(double plotScale) {
        return constructorWorldLiftAdjustment(plotScale);
    }

    private ParticleScalePolicy() {}
}
