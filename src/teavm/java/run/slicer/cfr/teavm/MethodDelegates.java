package run.slicer.cfr.teavm;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MethodDelegates {
    private MethodDelegates() {
    }

    public static void java_util_logging_Logger_setUseParentHandlers(Logger logger, boolean useParentHandlers) {
        // no-op
    }

    public static void java_util_logging_Logger_addHandler(Logger logger, Handler handler) {
        // no-op
    }

    public static void java_util_logging_Logger_setLevel(Logger logger, Level level) {
        // no-op
    }

    // CFR's LoggerFactory sets Level.WARNING, which the setLevel stub above discards, so below-WARNING
    // calls are dropped here instead. Otherwise they flood the console: logger.info runs once per
    // constant pool entry and logger.fine once per structured block.

    public static void java_util_logging_Logger_info(Logger logger, String msg) {
        // no-op
    }

    public static void java_util_logging_Logger_fine(Logger logger, String msg) {
        // no-op
    }

    public static void java_util_logging_Logger_finer(Logger logger, String msg) {
        // no-op
    }

    public static void java_util_logging_Logger_finest(Logger logger, String msg) {
        // no-op
    }
}
