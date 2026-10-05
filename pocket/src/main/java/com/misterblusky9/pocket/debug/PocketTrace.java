package com.misterblusky9.pocket.debug;

import com.misterblusky9.pym.api.Pym;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class PocketTrace {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Logger logger() {
        return LOGGER;
    }

    public static void scale(final String message, final Object... args) {
        if (Pym.debugging()) LOGGER.info("[PocketScale] " + message, args);
    }

    public static void debug(final String message, final Object... args) {
        if (Pym.debugging()) LOGGER.info(message, args);
    }

    public static void debugWarn(final String message, final Object... args) {
        if (Pym.debugging()) LOGGER.warn(message, args);
    }

    public static void warn(final String message, final Object... args) {
        if (Pym.debugging()) LOGGER.warn("[PocketScale] " + message, args);
    }

    private PocketTrace() {}
}
