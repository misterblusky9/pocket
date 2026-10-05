package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.physics.InternalForceScaleContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public final class InternalForces implements AutoCloseable {
    private final ServerSubLevel subLevel;

    private InternalForces(final ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public static InternalForces enter(final ServerSubLevel subLevel) {
        InternalForceScaleContext.enter(subLevel);
        return new InternalForces(subLevel);
    }

    @Override
    public void close() {
        InternalForceScaleContext.exit(this.subLevel);
    }
}
