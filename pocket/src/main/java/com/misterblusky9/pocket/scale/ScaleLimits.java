package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleBounds;

public final class ScaleLimits {
    public static final ScaleBounds SAFE = ScaleBounds.SAFE;
    public static final ScaleBounds CANNON_RELEASE = new ScaleBounds(ScaleBounds.SAFE.min(), ScaleBounds.FULL);

    public static boolean confirmsUnsupported(final ScaleBounds limits) {
        return limits != null && (limits.min() < SAFE.min() - ScaleBounds.EPSILON
                || limits.max() > SAFE.max() + ScaleBounds.EPSILON);
    }

    private ScaleLimits() {}
}
