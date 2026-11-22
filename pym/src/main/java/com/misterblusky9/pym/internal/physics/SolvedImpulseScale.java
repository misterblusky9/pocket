package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;

public final class SolvedImpulseScale {
    private static final ThreadLocal<ServerSubLevel> BODY = new ThreadLocal<>();

    public static boolean enter(@Nullable ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || BODY.get() != null) return false;
        BODY.set(subLevel);
        return true;
    }

    public static void exit() {
        BODY.remove();
    }

    @Nullable
    static double[] factorsFor(PhysicsPipelineBody body) {
        ServerSubLevel subLevel = BODY.get();
        if (subLevel == null || body != subLevel) return null;
        double scale = InternalForceScaleContext.scaleOf(subLevel);
        if (scale == 1.0D) return null;
        double[] own = InternalForceScaleContext.queuedFactors(subLevel);
        return new double[] {own[0] * scale, own[1]};
    }

    private SolvedImpulseScale() {}
}
