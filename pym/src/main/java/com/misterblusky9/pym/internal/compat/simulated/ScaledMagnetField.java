package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.SubLevel;

public final class ScaledMagnetField {
    public static final double REACH = 8.0D;

    public static double scaleOf(SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return ScaleBounds.FULL;
        double scale = Pym.scale().of(subLevel);
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    private ScaledMagnetField() {}
}
