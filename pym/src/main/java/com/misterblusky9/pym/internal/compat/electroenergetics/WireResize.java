package com.misterblusky9.pym.internal.compat.electroenergetics;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Set;
import java.util.UUID;

public final class WireResize {
    private static Boolean loaded;

    private WireResize() {}

    public static Object before(ServerLevel level, Set<UUID> resizing) {
        if (level == null || resizing.isEmpty() || !loaded()) return null;
        return WireResizeSpans.capture(level, resizing);
    }

    public static void after(Object captured) {
        if (captured != null) WireResizeSpans.apply(captured);
    }

    private static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("electroenergetics");
        return loaded;
    }
}
