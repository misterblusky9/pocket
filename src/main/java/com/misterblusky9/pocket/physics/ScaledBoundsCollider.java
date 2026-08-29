package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;

public final class ScaledBoundsCollider {
    public static void forgetSubLevel(final UUID id) {
        ScalePhysicsTransitions.forget(id);
        ColliderCoordinator.forget(id);
    }

    public static boolean statsAlreadyServedThisTick(final ServerSubLevel subLevel) {
        return ColliderCoordinator.statsAlreadyServedThisTick(subLevel);
    }

    public static void rebuildInPlace(final ServerSubLevel subLevel) {
        ColliderCoordinator.rebuild(subLevel);
    }

    public static void applyScaledLocalBounds(final ServerSubLevel subLevel) {
        ColliderCoordinator.applyScaledLocalBounds(subLevel);
    }

    public static boolean suppressNativeFluidProbe(final ServerSubLevel subLevel) {
        return ColliderCoordinator.suppressNativeFluidProbe(subLevel);
    }

    public static void restoreAfterNativeFluidProbe(final ServerSubLevel subLevel) {
        ColliderCoordinator.restoreAfterNativeFluidProbe(subLevel);
    }

    public static boolean hasSynthetic(final UUID id) {
        return ColliderCoordinator.hasSynthetic(id);
    }

    public static void restoreOriginal(final ServerSubLevel subLevel) {
        ColliderCoordinator.restoreOriginal(subLevel);
    }

    private ScaledBoundsCollider() {}
}
