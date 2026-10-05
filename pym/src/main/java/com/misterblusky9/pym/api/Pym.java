package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.physics.PlotShapeCache;
import com.misterblusky9.pym.internal.physics.RapierBridge;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.Nullable;

public final class Pym {
    public static final String MOD_ID = "pym";

    public static ScaleQuery scale() {
        return ScaleQuery.INSTANCE;
    }

    public static Frames frames() {
        return Frames.INSTANCE;
    }

    public static Resizing resize() {
        return Resizing.INSTANCE;
    }

    public static EntityScaling entities() {
        return EntityScaling.INSTANCE;
    }

    public static Connections connections() {
        return Connections.INSTANCE;
    }

    public static Extensions extensions() {
        return Extensions.INSTANCE;
    }

    public static InternalForces internalForces(final ServerSubLevel subLevel) {
        return InternalForces.enter(subLevel);
    }

    @Nullable
    public static SubLevelShape shape(final SubLevel subLevel) {
        return PlotShapeCache.get(subLevel);
    }

    public static boolean physicsLive(final ServerSubLevel subLevel) {
        return RapierBridge.isLive(subLevel);
    }

    public static boolean debugging() {
        return PymTrace.enabled();
    }

    private Pym() {}
}
