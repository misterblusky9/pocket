package com.misterblusky9.pym.internal.client;

public final class ProjectionNearPlanePolicy {
    public static final float VANILLA_NEAR_PLANE = 0.05F;
    public static final float MIN_SUBLEVEL_NEAR_PLANE = 0.0005F;

    public static float compose(float nearFromOtherMods, double subLevelScale) {
        if (!Float.isFinite(nearFromOtherMods) || nearFromOtherMods <= 0.0F
                || !Double.isFinite(subLevelScale) || subLevelScale <= 0.0D
                || subLevelScale >= 1.0D) {
            return nearFromOtherMods;
        }
        float subLevelNear = Math.max(
                MIN_SUBLEVEL_NEAR_PLANE,
                (float) (VANILLA_NEAR_PLANE * subLevelScale)
        );
        return Math.min(nearFromOtherMods, subLevelNear);
    }

    public static final float REFERENCE_FOV_DEGREES = 70.0F;

    public static float fovCorrected(float near, float fovRadians, float aspect) {
        if (!Float.isFinite(near) || near <= 0.0F || near >= VANILLA_NEAR_PLANE
                || !Float.isFinite(fovRadians) || fovRadians <= 0.0F || fovRadians >= (float) Math.PI
                || !Float.isFinite(aspect) || aspect <= 0.0F) {
            return near;
        }
        double reference = cornerReach(Math.toRadians(REFERENCE_FOV_DEGREES), aspect);
        double current = cornerReach(fovRadians, aspect);
        if (current <= reference) return near;
        float corrected = (float) (near * reference / current);
        return Math.max(corrected, Math.min(near, MIN_SUBLEVEL_NEAR_PLANE));
    }

    private static double cornerReach(double fovRadians, double aspect) {
        double vertical = Math.tan(fovRadians * 0.5D);
        double horizontal = vertical * aspect;
        return Math.sqrt(1.0D + vertical * vertical + horizontal * horizontal);
    }

    private ProjectionNearPlanePolicy() {}
}
