package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.config.PymConfig;

public final class RenderDetail {
    public static final double DEFAULT_RATIO = 16.0D;

    public static double maxRatio() {
        return PymConfig.detailCutoff();
    }

    public static boolean worthDrawing(final double detail, final double reference) {
        if (!ScaleBounds.isValid(detail) || !ScaleBounds.isValid(reference)) return true;
        final double ratio = reference / detail;
        final double max = maxRatio();
        return ratio < max || ScaleBounds.same(ratio, max);
    }

    private RenderDetail() {}
}
