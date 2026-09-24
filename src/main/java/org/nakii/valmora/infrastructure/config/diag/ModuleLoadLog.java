package org.nakii.valmora.infrastructure.config.diag;

import org.nakii.valmora.util.DebugManager;

import java.util.List;
import java.util.logging.Logger;

/**
 * The console format every content loader shares:
 * <pre>
 * [Items] 142 loaded from 9 files in 38 ms
 * [Mobs] 57 loaded from 4 files in 21 ms — 1 error, 2 warnings (1 kept previous version)
 *   ERROR [mobs/undead.yml] 'ghoul': missing required 'type'
 *   WARN  [mobs/undead.yml] 'ghoul' › drops[2].item: unknown item 'enchanted_flsh' (did you mean 'enchanted_flesh'?)
 * </pre>
 * and the final {@code Content: ...} line once a whole load pass is done.
 */
public final class ModuleLoadLog {

    private static final int DEFAULT_MAX_LINES = 20;

    private ModuleLoadLog() {}

    /**
     * Logs one content type's summary and records it (stats + diagnostics) in the global
     * {@link LoadReport}.
     */
    public static void publish(Logger logger, String typeName, int loaded, int files, long millis,
                               int keptPrevious, List<ConfigDiagnostic> diagnostics) {
        int errors = 0, warnings = 0;
        for (ConfigDiagnostic d : diagnostics) {
            if (d.severity() == Severity.ERROR) errors++;
            else if (d.severity() == Severity.WARN) warnings++;
        }
        LoadReport.global().addAll(diagnostics);
        LoadReport.global().recordLoaded(new LoadReport.TypeStats(typeName, loaded, files, millis, keptPrevious, errors, warnings));

        StringBuilder header = new StringBuilder("[").append(typeName).append("] ")
                .append(loaded).append(" loaded");
        if (files >= 0) header.append(" from ").append(files).append(files == 1 ? " file" : " files");
        header.append(" in ").append(millis).append(" ms");
        if (errors + warnings > 0) {
            header.append(" — ").append(counts(errors, warnings));
            if (keptPrevious > 0) header.append(" (").append(keptPrevious).append(" kept previous version)");
        }
        if (errors + warnings == 0) {
            logger.info(header.toString());
        } else {
            logger.warning(header.toString());
            logLines(logger, diagnostics, maxLines());
        }
        if (DebugManager.isEnabled("loader")) {
            for (ConfigDiagnostic d : diagnostics) {
                if (d.severity() == Severity.INFO) DebugManager.log("loader", d.format());
            }
        }
    }

    /** Logs up to {@code max} non-INFO diagnostics, then "... and N more". */
    public static void logLines(Logger logger, List<ConfigDiagnostic> diagnostics, int max) {
        int shown = 0, hidden = 0;
        for (ConfigDiagnostic d : diagnostics) {
            if (d.severity() == Severity.INFO) continue;
            if (shown >= max) {
                hidden++;
                continue;
            }
            logger.warning("  " + (d.severity() == Severity.ERROR ? "ERROR " : "WARN  ") + d.format());
            shown++;
        }
        if (hidden > 0) logger.warning("  ... and " + hidden + " more (see /valmora report)");
    }

    /** The one-line summary of a finished load pass. */
    public static void logOverall(Logger logger, LoadReport.Snapshot snapshot, String label) {
        long errors = snapshot.count(Severity.ERROR);
        long warnings = snapshot.count(Severity.WARN);
        String line = label + ": " + String.format("%,d", snapshot.totalLoaded()) + " entries across "
                + snapshot.types().size() + " types in " + snapshot.totalMillis() + " ms — "
                + (errors + warnings == 0 ? "no problems." : counts((int) errors, (int) warnings) + ". /valmora report for details.");
        if (errors + warnings == 0) logger.info(line);
        else logger.warning(line);
    }

    public static String counts(int errors, int warnings) {
        return errors + (errors == 1 ? " error, " : " errors, ") + warnings + (warnings == 1 ? " warning" : " warnings");
    }

    static int maxLines() {
        try {
            var plugin = org.nakii.valmora.Valmora.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                return Math.max(1, plugin.getConfig().getInt("diagnostics.max-console-lines-per-type", DEFAULT_MAX_LINES));
            }
        } catch (RuntimeException ignored) {
            // Not running inside a server (tests) — use the default.
        }
        return DEFAULT_MAX_LINES;
    }
}
