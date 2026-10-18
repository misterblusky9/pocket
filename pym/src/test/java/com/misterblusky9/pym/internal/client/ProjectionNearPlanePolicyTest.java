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
        System.out.println("PASS: " + checks + " near-plane composition checks");
    }
}
