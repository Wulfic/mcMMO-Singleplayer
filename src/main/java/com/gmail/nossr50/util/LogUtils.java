package com.gmail.nossr50.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug-logging helper, ported off {@code java.util.logging} onto SLF4J (the logging facade the
 * Fabric port already uses via {@code McMMOMod.LOGGER}).
 *
 * <p>Legacy call sites passed {@code mcMMO.p.getLogger()} (a JUL {@link java.util.logging.Logger})
 * and relied on {@code LogFilter} to gate the {@code [D]}-prefixed messages. In the port these are
 * routed at SLF4J {@code DEBUG} level, so the logging backend's own level config does the gating.
 */
public class LogUtils {

    public static final String DEBUG_STR = "[D] ";

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO");

    private LogUtils() {
    }

    /** Logs a debug message against mcMMO's shared logger. */
    public static void debug(@NotNull String message) {
        LOGGER.debug(DEBUG_STR + message);
    }

    /** Logs a debug message against a caller-supplied logger. */
    public static void debug(@NotNull Logger logger, @NotNull String message) {
        logger.debug(DEBUG_STR + message);
    }
}
