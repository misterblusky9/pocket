package com.misterblusky9.pocket.debug;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.UUID;

public final class PocketTrace {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final boolean PHYSICS = false;

    public static final boolean SCALE = false;

    private static volatile String lastNativeCall = "<none>";

    public static Logger logger() {
        return LOGGER;
    }

    public static String lastNativeCall() {
        return lastNativeCall;
    }

    public static void mark(final String call) {
        lastNativeCall = call;
    }

    public static void enter(final String call, final Object... args) {
        final String rendered = call + describe(args);
        lastNativeCall = rendered;
        if (PHYSICS) LOGGER.info("[PocketNative] >> {} thread={}", rendered, Thread.currentThread().getName());
    }

    public static void exit(final String call) {
        if (PHYSICS) LOGGER.info("[PocketNative] << {}", call);
    }

    public static void scale(final String message, final Object... args) {
        if (SCALE) LOGGER.info("[PocketScale] " + message, args);
    }

    public static String caller() {
        if (!SCALE) return "?";
        return StackWalker.getInstance().walk(frames -> frames
                .skip(1)
                .filter(frame -> !frame.getClassName().endsWith("PocketTrace"))
                .filter(frame -> !frame.getClassName().endsWith("ScaleController"))
                .findFirst()
                .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf(46) + 1)
                        + "#" + frame.getMethodName())
                .orElse("?"));
    }

    public static void warn(final String message, final Object... args) {
        LOGGER.warn("[PocketScale] " + message, args);
    }

    public static final boolean RENDER = false;

    private static final java.util.Map<String, Long> LAST_RENDER_LOG = new java.util.concurrent.ConcurrentHashMap<>();

    public static void render(final String key, final String message, final Object... args) {
        if (!RENDER) return;
        final long now = System.currentTimeMillis();
        final Long last = LAST_RENDER_LOG.get(key);
        if (last != null && now - last < 1000L) return;
        LAST_RENDER_LOG.put(key, now);
        LOGGER.info("[PocketRender] " + message, args);
    }

    public static String context(final ServerSubLevel subLevel) {
        final UUID id = subLevel == null ? null : subLevel.getUniqueId();
        final MinecraftServer server = subLevel == null || subLevel.getLevel() == null
                ? null
                : subLevel.getLevel().getServer();
        return "uuid=" + id
                + " tick=" + (server == null ? -1 : server.getTickCount())
                + " thread=" + Thread.currentThread().getName()
                + " inPhysicsStep=" + SubLevelPhysicsSystem.IN_PHYSICS_STEP
                + " serverThread=" + (server != null && server.isSameThread());
    }

    public static boolean isUnsafeMutationPoint(final ServerSubLevel subLevel) {
        if (SubLevelPhysicsSystem.IN_PHYSICS_STEP) return true;
        final MinecraftServer server = subLevel == null || subLevel.getLevel() == null
                ? null
                : subLevel.getLevel().getServer();
        return server != null && !server.isSameThread();
    }

    private static String describe(final Object[] args) {
        if (args == null || args.length == 0) return "";
        final StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append(args[i]);
        }
        return builder.append(')').toString();
    }

    private PocketTrace() {}
}
