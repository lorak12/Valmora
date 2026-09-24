package org.nakii.valmora.infrastructure.config.diag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Everything the content loaders reported during one load pass (startup, full reload, partial
 * reload): diagnostics plus a per-type loaded count. {@code ModuleManager} brackets each pass with
 * {@link #begin()} / {@link #complete(String)}; the last completed pass is kept for {@code /valmora report}.
 */
public final class LoadReport {

    /** Per content type: what one loader loaded and what went wrong. */
    public record TypeStats(String category, int loaded, int files, long millis, int keptPrevious,
                            int errors, int warnings) {}

    /** A finished load pass. */
    public record Snapshot(String label, Instant completedAt, List<ConfigDiagnostic> diagnostics, List<TypeStats> types) {
        public static final Snapshot EMPTY = new Snapshot("none", null, List.of(), List.of());

        public long count(Severity severity) {
            return diagnostics.stream().filter(d -> d.severity() == severity).count();
        }

        public int totalLoaded() {
            return types.stream().mapToInt(TypeStats::loaded).sum();
        }

        public long totalMillis() {
            return types.stream().mapToLong(TypeStats::millis).sum();
        }
    }

    /** Hard cap so a pathological config can't grow the report without bound. */
    private static final int MAX_DIAGNOSTICS = 10_000;

    private static final LoadReport GLOBAL = new LoadReport();

    public static LoadReport global() {
        return GLOBAL;
    }

    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private final List<TypeStats> types = new ArrayList<>();
    private int dropped;
    private volatile Snapshot lastCompleted = Snapshot.EMPTY;

    /** Starts a new load pass, discarding anything not yet completed. */
    public synchronized void begin() {
        diagnostics.clear();
        types.clear();
        dropped = 0;
    }

    public synchronized void add(ConfigDiagnostic diagnostic) {
        if (diagnostic == null) return;
        if (diagnostics.size() >= MAX_DIAGNOSTICS) {
            dropped++;
            return;
        }
        diagnostics.add(diagnostic);
    }

    public synchronized void addAll(Collection<ConfigDiagnostic> all) {
        for (ConfigDiagnostic d : all) add(d);
    }

    public synchronized void recordLoaded(TypeStats stats) {
        types.add(stats);
    }

    /** The diagnostics collected so far in the current pass. */
    public synchronized List<ConfigDiagnostic> current() {
        return List.copyOf(diagnostics);
    }

    public synchronized List<TypeStats> currentTypes() {
        return List.copyOf(types);
    }

    public synchronized int droppedCount() {
        return dropped;
    }

    /** Ends the pass: returns and clears everything collected, and keeps it as {@link #lastCompleted()}. */
    public synchronized Snapshot complete(String label) {
        Snapshot snapshot = new Snapshot(label, Instant.now(), List.copyOf(diagnostics), List.copyOf(types));
        diagnostics.clear();
        types.clear();
        dropped = 0;
        lastCompleted = snapshot;
        return snapshot;
    }

    /** Returns and clears the diagnostics collected so far, without ending the pass. */
    public synchronized List<ConfigDiagnostic> drain() {
        List<ConfigDiagnostic> copy = List.copyOf(diagnostics);
        diagnostics.clear();
        return copy;
    }

    public Snapshot lastCompleted() {
        return lastCompleted;
    }

    /** Formatted ERROR lines only — the shape the old string report had. */
    public static List<String> errorLines(List<ConfigDiagnostic> diagnostics) {
        List<String> out = new ArrayList<>();
        for (ConfigDiagnostic d : diagnostics) if (d.isError()) out.add(d.format());
        return Collections.unmodifiableList(out);
    }
}
