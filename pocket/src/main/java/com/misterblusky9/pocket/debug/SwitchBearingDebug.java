package com.misterblusky9.pocket.debug;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class SwitchBearingDebug {
    public static final boolean ENABLED = false;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[PocketSwitchBearing] ";

    public static void info(String message, Object... args) {
        if (ENABLED) {
            LOGGER.info(PREFIX + message, args);
        }
    }

    public static void warn(String message, Object... args) {
        if (ENABLED) {
            LOGGER.warn(PREFIX + message, args);
        }
    }

    private SwitchBearingDebug() {
    }
}
