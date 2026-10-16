package com.misterblusky9.pym.internal.compat.ponder;

import net.minecraft.world.phys.Vec3;

public interface PonderSectionScaleAccess {
    void pym$setPivot(Vec3 pivot);

    double pym$scale();

    void pym$setScale(double scale, boolean immediate);
}
