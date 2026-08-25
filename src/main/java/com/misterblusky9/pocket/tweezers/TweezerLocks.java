package com.misterblusky9.pocket.tweezers;

import com.misterblusky9.pocket.debug.PocketTrace;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TweezerLocks {
    private static final String HANDLER =
            "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler";

    private static boolean resolved;
    private static Method get;
    private static Method toggle;
    private static Method removeLock;
    private static Method isLocked;

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;

        try {
            final Class<?> handler = Class.forName(HANDLER);
            get = handler.getMethod("get", ServerLevel.class);
            toggle = handler.getMethod("toggleLock", UUID.class);
            removeLock = handler.getMethod("removeLock", SubLevel.class);
            isLocked = handler.getMethod("isLocked", SubLevel.class);
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            PocketTrace.warn("physics staff lock registry unavailable: {}", exception.toString());
            get = null;
            toggle = null;
            removeLock = null;
            isLocked = null;
        }
    }

    public static void toggle(final ServerLevel level, final UUID subLevelId) {
        resolve();
        if (level == null || subLevelId == null || get == null || toggle == null) return;

        try {
            final Object handler = get.invoke(null, level);
            if (handler != null) toggle.invoke(handler, subLevelId);
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            PocketTrace.warn("could not toggle lock for {}: {}", subLevelId, exception.toString());
        }
    }

    public static void remove(final ServerLevel level, final SubLevel subLevel) {
        resolve();
        if (level == null || subLevel == null || get == null || removeLock == null) return;

        try {
            final Object handler = get.invoke(null, level);
            if (handler != null) removeLock.invoke(handler, subLevel);
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            PocketTrace.warn("could not clear lock on {}: {}", subLevel.getUniqueId(), exception.toString());
        }
    }

    public static boolean locked(final ServerLevel level, final SubLevel subLevel) {
        resolve();
        if (level == null || subLevel == null || get == null || isLocked == null) return false;

        try {
            final Object handler = get.invoke(null, level);
            return handler != null && (boolean) isLocked.invoke(handler, subLevel);
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private TweezerLocks() {}
}
