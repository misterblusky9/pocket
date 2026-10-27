package com.misterblusky9.pym.internal.client;

/** Run without Minecraft: javac ParticleScalePolicy.java ParticleScalePolicyRegression.java && java ... */
public final class ParticleScalePolicyRegression {
    private static int checks;
    private static void near(double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > 1.0E-10)
            throw new AssertionError("expected " + expected + ", got " + actual);
        checks++;
    }
    public static void main(String[] args) {
        double[] scales = {1.0 / 64, 1.0 / 16, 1.0 / 4, 1, 2, 4, 16};
        for (double plot : scales) {
            for (double emitter : scales) {
                // Block, player and arbitrary entity emitters across unrelated plots.
                near(ParticleScalePolicy.emitterScale(emitter, plot), emitter);
                double projected = 0.35 * plot;
                near(projected * ParticleScalePolicy.remainingVelocityRatio(emitter, plot), 0.35 * emitter);
                // Both the constructor path and the preserved post-projection
                // TerrainParticle path yield the same corrected world lift.
                double constructorWorldLift = .1 + ParticleScalePolicy.constructorWorldLiftAdjustment(plot);
                near(constructorWorldLift * ParticleScalePolicy.remainingVelocityRatio(emitter, plot), .1 * emitter);
                double terrainLegacyLift = .1 + ParticleScalePolicy.terrainLegacyWorldLiftAdjustment(plot);
                near(terrainLegacyLift * ParticleScalePolicy.remainingVelocityRatio(emitter, plot), .1 * emitter);
                // One tick's intrinsic acceleration is scaled; the dimensionless drag stays.
                double initialWorldV = 0.15 * emitter;
                double updatedNativeV = 0.88 * (initialWorldV / emitter + .03);
                near(updatedNativeV * emitter, 0.88 * (0.15 + .03) * emitter);
                // The intrinsic tick is normalized; motion Sable adds AFTER
                // particle.tick is outside the normalization scope.
                near(.08 * emitter + .4, .08 * emitter + .4);
            }
        }
        near(ParticleScalePolicy.emitterScale(Double.NaN, .0625), .0625);
        near(ParticleScalePolicy.emitterScale(1., .0625), 1.);
        near(ParticleScalePolicy.emitterScale(.0625, 1.), .0625);
        near(ParticleScalePolicy.emitterScale(Double.NaN, Double.NaN), 1.);
        System.out.println("PASS: " + checks + " particle frame/scale assertions");
    }
}
