package org.nakii.valmora.infrastructure.config.diag;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Reports a problem into the current {@link LoadScope}, or — when nothing is being loaded (content
 * compiled at runtime) — logs it once to the console so repeated executions don't spam.
 */
public final class Diagnostics {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final int MAX_REMEMBERED = 5_000;

    private Diagnostics() {}

    public static void error(String message) {
        report(Severity.ERROR, message, null);
    }

    public static void error(String message, String hint) {
        report(Severity.ERROR, message, hint);
    }

    public static void warn(String message) {
        report(Severity.WARN, message, null);
    }

    public static void warn(String message, String hint) {
        report(Severity.WARN, message, hint);
    }

    public static void report(Severity severity, String message, String hint) {
        var scope = LoadScope.current();
        if (scope.isPresent()) {
            scope.get().report(severity, message, hint);
            return;
        }
        if (severity == Severity.INFO) return;
        String line = message + (hint != null ? " (" + hint + ")" : "");
        if (LOGGED.size() > MAX_REMEMBERED) LOGGED.clear();
        if (LOGGED.add(line)) logger().warning("[DSL] " + line);
    }

    /** Forgets which runtime messages were already logged (on reload). */
    public static void resetRuntimeDedup() {
        LOGGED.clear();
    }

    static Logger logger() {
        var plugin = org.nakii.valmora.Valmora.getInstance();
        return plugin != null && plugin.getLogger() != null ? plugin.getLogger() : Logger.getLogger("Valmora");
    }
}
