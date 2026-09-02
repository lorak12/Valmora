package org.nakii.valmora.util;

import org.nakii.valmora.Valmora;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which modules currently have verbose debug logging turned on, toggled via
 * {@code /valmora debug <module>} (or {@code /valmora debug all}). Deliberately dumb — a global,
 * in-memory, case-insensitive set of module ids plus one "everything" flag — so any module can gate
 * its own logging behind {@link #isEnabled(String)}/{@link #log(String, String)} without wiring
 * anything else up. Not persisted across restarts; a reload does not clear it (it's orthogonal to
 * module state, purely an operator toggle for the current server session).
 */
public final class DebugManager {

    private static final Set<String> ENABLED = ConcurrentHashMap.newKeySet();
    private static final String ALL = "all";
    private static volatile boolean allEnabled = false;

    private DebugManager() {
    }

    /** True if {@code moduleId} was individually toggled on, or the global "all" switch is on. */
    public static boolean isEnabled(String moduleId) {
        if (allEnabled) return true;
        return moduleId != null && ENABLED.contains(moduleId.toLowerCase());
    }

    /**
     * Flips the module's debug flag (or the global "all" switch, for {@code moduleId == "all"}) and
     * returns the new state (true = now enabled). Toggling "all" off does not clear individually
     * enabled modules — they simply go back to being the only thing gating {@link #isEnabled}.
     */
    public static boolean toggle(String moduleId) {
        String key = moduleId.toLowerCase();
        if (key.equals(ALL)) {
            allEnabled = !allEnabled;
            return allEnabled;
        }
        if (!ENABLED.add(key)) {
            ENABLED.remove(key);
            return false;
        }
        return true;
    }

    /** True if the global "all" switch is on (independent of any individual module toggle). */
    public static boolean isAllEnabled() {
        return allEnabled;
    }

    /** @return every individually-toggled-on module id (does not include "all" itself). */
    public static Set<String> getEnabledModules() {
        return Set.copyOf(ENABLED);
    }

    /**
     * Convenience one-liner for call sites that don't want to hand-roll their own gated logger:
     * logs {@code "[<moduleId>-debug] <msg>"} via the plugin logger, but only when debug logging for
     * {@code moduleId} (or "all") is enabled. A no-op (not even a string concat, since the caller's
     * message is a plain arg, not a supplier) when disabled — callers building an expensive message
     * should still guard with {@code DebugManager.isEnabled(id)} themselves first.
     */
    public static void log(String moduleId, String msg) {
        if (!isEnabled(moduleId)) return;
        Valmora plugin = Valmora.getInstance();
        if (plugin == null) return;
        plugin.getLogger().info("[" + moduleId + "-debug] " + msg);
    }
}
