package com.misterblusky9.pym.internal.client;

public class ProjectionNearPlanePolicyTest {
    private static int checks;
    static void near(float value, float expected, String label) {
        if (Math.abs(value - expected) > 1e-8F) {
            throw new AssertionError(label + " expected " + expected + " got " + value);
        }
        checks++;
    }
    public static void main(String[] args) {
        float vanilla = 0.05F;
        double s16 = 1.0D/16.0D;
        double s64 = 1.0D/64.0D;
        near(ProjectionNearPlanePolicy.compose(vanilla, 1), vanilla, "1x/1x");
        near(ProjectionNearPlanePolicy.compose(vanilla, s16), 0.003125F, "1x entity/1-16 sublevel");
        near(ProjectionNearPlanePolicy.compose(0.003125F, 1), 0.003125F, "1-16 entity/1x sublevel");
        near(ProjectionNearPlanePolicy.compose(0.003125F, s16), 0.003125F, "1-16 entity/1-16 sublevel (NOT product)");
        near(ProjectionNearPlanePolicy.compose(0.00078125F, s16), 0.00078125F, "1-64 entity/1-16 sublevel");
        near(ProjectionNearPlanePolicy.compose(0.003125F, s64), 0.00078125F, "1-16 entity/1-64 sublevel");
        near(ProjectionNearPlanePolicy.compose(vanilla, 1.0D/256), 0.0005F, "sublevel floor");
        near(ProjectionNearPlanePolicy.compose(0.0001953125F, 1.0D/256), 0.0001953125F, "honor smaller third party near plane");
        near(ProjectionNearPlanePolicy.compose(vanilla, 0), vanilla, "invalid zero scale");
        near(ProjectionNearPlanePolicy.compose(vanilla, Double.NaN), vanilla, "invalid NaN scale");
        near(ProjectionNearPlanePolicy.compose(vanilla, Double.POSITIVE_INFINITY), vanilla, "invalid inf scale");
        if (!Float.isNaN(ProjectionNearPlanePolicy.compose(Float.NaN, s16))) throw new AssertionError("invalid preexisting NaN");
        checks++;
        float wide = 16.0F / 9.0F;
        float fov70 = (float) Math.toRadians(70);
        float fov110 = (float) Math.toRadians(110);
        near(ProjectionNearPlanePolicy.fovCorrected(vanilla, fov110, wide), vanilla, "1x camera keeps vanilla near at any fov");
        near(ProjectionNearPlanePolicy.fovCorrected(0.003125F, fov70, wide), 0.003125F, "tiny camera at reference fov unchanged");
        near(ProjectionNearPlanePolicy.fovCorrected(0.003125F, (float) Math.toRadians(50), wide), 0.003125F, "narrow fov never raises near");
        float wideTiny = ProjectionNearPlanePolicy.fovCorrected(0.003125F, fov110, wide);
        double t70 = Math.tan(Math.toRadians(35)), t110 = Math.tan(Math.toRadians(55));
        double reach70 = Math.sqrt(1 + t70 * t70 + t70 * t70 * wide * wide);
        double reach110 = Math.sqrt(1 + t110 * t110 + t110 * t110 * wide * wide);
        near(wideTiny, (float) (0.003125D * reach70 / reach110), "tiny camera at 110 keeps 70-degree corner reach");
        near((float) (wideTiny * reach110), (float) (0.003125D * reach70), "corner distance matches reference");
        near(ProjectionNearPlanePolicy.fovCorrected(0.0006F, (float) Math.toRadians(170), wide), 0.0005F, "fov correction floor");
        near(ProjectionNearPlanePolicy.fovCorrected(0.0001F, (float) Math.toRadians(170), wide), 0.0001F, "fov correction never lowers below an already smaller near");
        near(ProjectionNearPlanePolicy.fovCorrected(0.003125F, Float.NaN, wide), 0.003125F, "invalid fov");
        near(ProjectionNearPlanePolicy.fovCorrected(0.003125F, fov110, 0), 0.003125F, "invalid aspect");
        System.out.println("PASS: " + checks + " near-plane composition checks");
    }
}
