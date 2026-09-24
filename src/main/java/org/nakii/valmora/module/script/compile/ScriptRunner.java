package org.nakii.valmora.module.script.compile;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.module.script.event.ConditionAbortException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a compiled script from an event listener or task. A {@code condition} line that stops the
 * script ({@link ConditionAbortException}) is a normal outcome here, not an error; anything else a
 * script throws is logged — once per source per minute, naming the file and entry it came from —
 * instead of propagating into the Bukkit event pipeline.
 */
public final class ScriptRunner {

    private static final long LOG_INTERVAL_MS = 60_000L;
    private static final Map<String, Long> LAST_LOGGED = new ConcurrentHashMap<>();

    private ScriptRunner() {}

    /** @return true if the script ran to the end, false if a condition stopped it or it failed */
    public static boolean run(CompiledEvent event, ExecutionContext context, ConfigSource source) {
        if (event == null) return true;
        try {
            event.execute(context);
            return true;
        } catch (ConditionAbortException stop) {
            return false;
        } catch (RuntimeException e) {
            report(source, e);
            return false;
        }
    }

    static void report(ConfigSource source, RuntimeException e) {
        String where = source == null ? "unknown source" : source.describe();
        long now = System.currentTimeMillis();
        Long last = LAST_LOGGED.get(where);
        if (last != null && now - last < LOG_INTERVAL_MS) return;
        if (LAST_LOGGED.size() > 1_000) LAST_LOGGED.clear();
        LAST_LOGGED.put(where, now);
        logger().log(Level.WARNING, "[Script] A script from " + where + " failed: " + e, e);
    }

    private static Logger logger() {
        var plugin = org.nakii.valmora.Valmora.getInstance();
        return plugin != null && plugin.getLogger() != null ? plugin.getLogger() : Logger.getLogger("Valmora");
    }
}
