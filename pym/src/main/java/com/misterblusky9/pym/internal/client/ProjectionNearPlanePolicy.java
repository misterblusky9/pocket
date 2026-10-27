package com.misterblusky9.pym.internal.client;

/**
 * Composes an already-scaled near plane with Pym's sublevel scale.
 *
 * <p>Pehkui changes the 0.05F projection constant based on entity scale.
 * Pym must not scale that already-modified value a second time. Both scales
 * are absolute world scales, not successive parent/child transformations.
 */
public final class ProjectionNearPlanePolicy {
    public static final float VANILLA_NEAR_PLANE = 0.05F;
    public static final float MIN_SUBLEVEL_NEAR_PLANE = 0.0005F;

    public static float compose(float nearFromOtherMods, double subLevelScale) {
        // Preserve third-party decisions and fail safe outside valid ranges.
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
