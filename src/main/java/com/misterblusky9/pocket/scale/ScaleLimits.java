package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;

public record ScaleLimits(double min, double max) {
    public static final ScaleLimits API = new ScaleLimits(PocketSized.MIN_SCALE, PocketSized.MAX_SCALE);
    public static final ScaleLimits EXPERIMENTAL = new ScaleLimits(PocketSized.EXPERIMENTAL_MIN_SCALE, PocketSized.EXPERIMENTAL_MAX_SCALE);
    public static final ScaleLimits CREATIVE = new ScaleLimits(PocketSized.CREATIVE_MIN_SCALE, PocketSized.CREATIVE_MAX_SCALE);
    public static final ScaleLimits STANDARD = new ScaleLimits(PocketSized.SURVIVAL_MIN_SCALE, PocketSized.STANDARD_MAX_SCALE);
    public static final ScaleLimits CANNON_RELEASE = new ScaleLimits(PocketSized.CREATIVE_MIN_SCALE, PocketSized.FULL_SCALE);

    public double clamp(final double scale) {
        return Math.max(this.min, Math.min(this.max, scale));
    }

    public boolean permits(final double from, final double to) {
        if (!PocketSized.isValidScale(to)) return false;
        final boolean aboveFloor = to >= this.min - PocketSized.EPSILON || to >= from - PocketSized.EPSILON;
        final boolean belowCeiling = to <= this.max + PocketSized.EPSILON || to <= from + PocketSized.EPSILON;
        return aboveFloor && belowCeiling;
    }
}
