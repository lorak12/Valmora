package org.nakii.valmora.infrastructure.config.diag;

import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Writes the full result of the last load pass to {@code plugins/Valmora/last-load-report.txt}
 * (config {@code diagnostics.write-report-file}, default on) — the console only shows the first
 * few problems per type.
 */
public final class ReportFile {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ReportFile() {}

    public static void writeIfEnabled(Valmora plugin, LoadReport.Snapshot snapshot) {
        try {
            if (plugin.getConfig() != null && !plugin.getConfig().getBoolean("diagnostics.write-report-file", true)) return;
            File folder = plugin.getDataFolder();
            if (folder == null) return;
            folder.mkdirs();
            Files.writeString(new File(folder, "last-load-report.txt").toPath(), render(snapshot), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not write last-load-report.txt: " + e.getMessage());
        }
    }

    public static String render(LoadReport.Snapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Valmora content load report — ").append(snapshot.label());
        if (snapshot.completedAt() != null) sb.append(" at ").append(TIME.format(snapshot.completedAt()));
        sb.append('\n');
        sb.append(snapshot.totalLoaded()).append(" entries across ").append(snapshot.types().size())
                .append(" types in ").append(snapshot.totalMillis()).append(" ms — ")
                .append(ModuleLoadLog.counts((int) snapshot.count(Severity.ERROR), (int) snapshot.count(Severity.WARN)))
                .append("\n\n");
        sb.append("Types:\n");
        for (LoadReport.TypeStats t : snapshot.types()) {
            sb.append(String.format("  %-24s %6d loaded  %4d files  %5d ms", t.category(), t.loaded(), Math.max(0, t.files()), t.millis()));
            if (t.errors() + t.warnings() > 0) sb.append("  ").append(ModuleLoadLog.counts(t.errors(), t.warnings()));
            if (t.keptPrevious() > 0) sb.append("  (").append(t.keptPrevious()).append(" kept previous)");
            sb.append('\n');
        }
        for (Severity severity : Severity.values()) {
            boolean header = false;
            for (ConfigDiagnostic d : snapshot.diagnostics()) {
                if (d.severity() != severity) continue;
                if (!header) {
                    sb.append('\n').append(severity).append(":\n");
                    header = true;
                }
                sb.append("  ");
                if (d.category() != null) sb.append('(').append(d.category()).append(") ");
                sb.append(d.format()).append('\n');
            }
        }
        return sb.toString();
    }
}
