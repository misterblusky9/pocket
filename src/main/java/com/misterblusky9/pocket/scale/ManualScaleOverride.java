package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.network.CompressionSyncPayload;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ManualScaleOverride {
    public static final int SUSPEND_TICKS = 60;

    private static final Map<UUID, Long> SUSPENDED = new ConcurrentHashMap<>();

    public static void engage(final ServerSubLevel subLevel, final long gameTime) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;

        final UUID id = subLevel.getUniqueId();
        final boolean fresh = !isSuspended(id, gameTime);
        SUSPENDED.put(id, gameTime + SUSPEND_TICKS);
        if (fresh) CompressionSyncPayload.sendRelease(subLevel);
    }

    public static void sustain(final UUID subLevelId, final long gameTime) {
        if (subLevelId == null) return;
        SUSPENDED.put(subLevelId, gameTime + SUSPEND_TICKS);
    }

    public static boolean isSuspended(final UUID subLevelId, final long gameTime) {
        if (subLevelId == null) return false;

        final Long until = SUSPENDED.get(subLevelId);
        if (until == null) return false;
        if (gameTime > until) {
            SUSPENDED.remove(subLevelId, until);
            return false;
        }
        return true;
    }

    public static void clear(final UUID subLevelId) {
        if (subLevelId != null) SUSPENDED.remove(subLevelId);
    }

    private ManualScaleOverride() {}
}
