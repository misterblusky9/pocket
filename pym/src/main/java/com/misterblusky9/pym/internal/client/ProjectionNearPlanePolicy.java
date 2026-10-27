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

    private ProjectionNearPlanePolicy() {}
}
