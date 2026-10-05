package com.misterblusky9.pym.internal.debug;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.UUID;

public final class PymTrace {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final boolean PHYSICS = false;

    private static volatile boolean enabled;

    private static volatile String lastNativeCall = "<none>";

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

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
        if (PHYSICS) LOGGER.info("[PymNative] >> {} thread={}", rendered, Thread.currentThread().getName());
    }

    public static void exit(final String call) {
        if (PHYSICS) LOGGER.info("[PymNative] << {}", call);
    }

    public static void scale(final String message, final Object... args) {
        if (enabled) LOGGER.info("[PymScale] " + message, args);
    }

    public static String caller() {
        if (!enabled) return "?";
        return StackWalker.getInstance().walk(frames -> frames
                .skip(1)
                .filter(frame -> !frame.getClassName().endsWith("PymTrace"))
                .findFirst()
                .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf(46) + 1)
                        + "#" + frame.getMethodName())
                .orElse("?"));
    }

    public static void warn(final String message, final Object... args) {
        if (enabled) LOGGER.warn("[PymScale] " + message, args);
    }

    public static void debug(final String message, final Object... args) {
        if (enabled) LOGGER.info(message, args);
    }

    public static void debugWarn(final String message, final Object... args) {
        if (enabled) LOGGER.warn(message, args);
    }

    private static final java.util.Map<String, Long> LAST_RENDER_LOG = new java.util.concurrent.ConcurrentHashMap<>();

    public static void render(final String key, final String message, final Object... args) {
        if (!enabled) return;
        final long now = System.currentTimeMillis();
        final Long last = LAST_RENDER_LOG.get(key);
        if (last != null && now - last < 1000L) return;
        LAST_RENDER_LOG.put(key, now);
        LOGGER.info("[PymRender] " + message, args);
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

    private PymTrace() {}
}
