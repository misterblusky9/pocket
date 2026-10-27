package com.misterblusky9.pym.internal.client;

/**
 * All scales are physical, world-space ratios (1.0 is a normal-sized emitter).
 * This policy contains no Minecraft types to make the coordinate-frame rules testable.
 */
public final class ParticleScalePolicy {
    public static boolean valid(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    /** Explicit entity/block provenance always wins, including an explicit 1x. */
    public static double emitterScale(double explicitScale, double originScale) {
        return valid(explicitScale) ? explicitScale : valid(originScale) ? originScale : 1.0D;
    }

    /** Sable projects local initial velocity by the plot ratio before Pym sees it. */
    public static double remainingVelocityRatio(double emitterScale, double projectionScale) {
        return emitterScale / (valid(projectionScale) ? projectionScale : 1.0D);
    }

    /**
     * Sable 2.0.6 adds +0.1 of WORLD Y to six-argument Particle constructors
     * by adding its inverse-transformed vector to LOCAL velocity. We change the
     * world impulse to +0.1 * plotScale while the velocity is still local.
     * Later, normal projection and the emitter/plot velocity ratio produce the
     * final +0.1 * emitterScale; no particle-class checks are required.
     */
    public static double constructorWorldLiftAdjustment(double plotScale) {
        return valid(plotScale) ? 0.1D * (plotScale - 1.0D) : 0.0D;
    }

    /**
     * Legacy TerrainParticle.setPower can reset the constructor's +0.1 offset.
     * Apply this correction AFTER Sable's projection but BEFORE emitter/plot
     * velocity normalization. At 1/16 on 1/16 it is the original proven
     * terrain lift fix: +0.1 world Y becomes +0.00625 world Y.
     */
    public static double terrainLegacyWorldLiftAdjustment(double plotScale) {
        return constructorWorldLiftAdjustment(plotScale);
    }

    private ParticleScalePolicy() {}
}
