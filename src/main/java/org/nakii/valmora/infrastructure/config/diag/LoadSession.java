package org.nakii.valmora.infrastructure.config.diag;

import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Diagnostics for loaders that read their YAML themselves instead of through {@code YamlLoader}
 * (single settings files like {@code rarities.yml}, quest packages, pipelines, ...). Their parsing
 * stays their own; this only gives them the same reporting: syntax errors surfaced instead of a
 * silently empty file, problems attributed to file and entry, one summary line, and everything in
 * the reload report.
 * <pre>{@code
 * try (LoadSession session = LoadSession.open(plugin, "Rarities", "rarities.yml")) {
 *     YamlConfiguration yaml = session.readYaml(file, "rarities.yml");
 *     if (yaml == null) return;                       // syntax error, already reported
 *     for (String key : yaml.getKeys(false)) {
 *         try (LoadScope scope = session.entry("rarities.yml", key)) {
 *             ... parse, reporting through scope / ConfigReader ...
 *             session.loaded();
 *         }
 *     }
 * }
 * }</pre>
 */
public final class LoadSession implements AutoCloseable {

    private final Logger logger;
    private final String category;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private final Set<String> files = new HashSet<>();
    private final long start = System.nanoTime();
    private int loaded;
    private boolean closed;

    private LoadSession(Logger logger, String category) {
        this.logger = logger;
        this.category = category;
    }

    /**
     * @param category    display name of the content type, e.g. {@code "Rarities"}
     * @param filePrefixes files/folders (relative to the data folder) this loader owns — references
     *                    previously recorded from them are dropped, since they're about to be re-read
     */
    public static LoadSession open(Valmora plugin, String category, String... filePrefixes) {
        Logger logger = plugin != null && plugin.getLogger() != null ? plugin.getLogger() : Logger.getLogger("Valmora");
        for (String prefix : filePrefixes) {
            org.nakii.valmora.infrastructure.config.refs.ReferenceIndex.global().clearFiles(prefix);
        }
        return new LoadSession(logger, category);
    }

    /**
     * Reads a YAML file, reporting a syntax error (and returning null) instead of silently
     * returning an empty configuration like {@code YamlConfiguration.loadConfiguration} does.
     * A missing file also returns null, without a report.
     */
    public YamlConfiguration readYaml(File file, String relativePath) {
        if (file == null || !file.isFile()) return null;
        files.add(relativePath);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            int nl = msg.indexOf('\n');
            diagnostics.add(new ConfigDiagnostic(Severity.ERROR, category, relativePath, null, null,
                    "Invalid YAML — " + (nl < 0 ? msg : msg.substring(0, nl)) + " — nothing in this file was loaded", null));
            return null;
        }
    }

    /** Opens (and makes current) a scope for one entry of {@code file}; close it when done. */
    public LoadScope entry(String file, String entryId) {
        if (file != null) files.add(file);
        return LoadScope.enter(category, file, entryId, diagnostics::add);
    }

    /** A scope for problems about a file as a whole (no entry). */
    public LoadScope file(String file) {
        return entry(file, null);
    }

    public void error(String file, String entryId, String message) {
        diagnostics.add(new ConfigDiagnostic(Severity.ERROR, category, file, entryId, null, message, null));
    }

    public void warn(String file, String entryId, String message) {
        warn(file, entryId, message, null);
    }

    public void warn(String file, String entryId, String message, String hint) {
        diagnostics.add(new ConfigDiagnostic(Severity.WARN, category, file, entryId, null, message, hint));
    }

    /** Counts one successfully loaded entry. */
    public void loaded() {
        loaded++;
    }

    public void loaded(int n) {
        loaded += n;
    }

    public int loadedCount() {
        return loaded;
    }

    public List<ConfigDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    /** Logs the summary line (and problems) and records everything in the load report. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ModuleLoadLog.publish(logger, category, loaded, files.size(), (System.nanoTime() - start) / 1_000_000L, 0, diagnostics);
    }
}
