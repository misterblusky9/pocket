package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public final class MassScaleContext {
    private static final ThreadLocal<ServerSubLevel> ACTIVE = new ThreadLocal<>();

    public static void enter(final ServerSubLevel subLevel) { ACTIVE.set(subLevel); }
    public static void exit(final ServerSubLevel subLevel) {
        if (ACTIVE.get() == subLevel) ACTIVE.remove();
    }
    public static boolean activeFor(final ServerSubLevel subLevel) { return ACTIVE.get() == subLevel; }

    private MassScaleContext() {}
}
