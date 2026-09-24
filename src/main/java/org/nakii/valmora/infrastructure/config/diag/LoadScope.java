package org.nakii.valmora.infrastructure.config.diag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The file/entry currently being loaded on this thread. Loaders open one around each entry they
 * parse ({@code try (var scope = LoadScope.enter(...)) { ... }}); anything that runs inside — a
 * {@code ConfigReader}, the script DSL compilers, event factories — reports problems through
 * {@link #current()} instead of needing the source threaded through every signature.
 *
 * <p>With no scope open (content compiled at runtime) {@link Diagnostics} falls back to a
 * deduplicated console warning.
 */
public final class LoadScope implements AutoCloseable {

    private static final ThreadLocal<Deque<LoadScope>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /** Hook for {@link #ref}: installed by the reference index. */
    public interface RefRecorder {
        void record(String kind, String id, ConfigSource source);
    }

    private static volatile RefRecorder refRecorder;

    public static void setRefRecorder(RefRecorder recorder) {
        refRecorder = recorder;
    }

    private final String category;
    private final String file;
    private final String entryId;
    private final String path;
    private final DiagnosticSink sink;
    private final Counters counters;
    private boolean pushed;

    /** Shared by an entry scope and all its {@link #sub} scopes. */
    private static final class Counters {
        int errors;
        int warnings;
        final Set<String> locals = new HashSet<>();
    }

    private LoadScope(String category, String file, String entryId, String path, DiagnosticSink sink, Counters counters) {
        this.category = category;
        this.file = file;
        this.entryId = entryId;
        this.path = path;
        this.sink = sink;
        this.counters = counters;
    }

    /** Opens a scope reporting into the global {@link LoadReport}. */
    public static LoadScope enter(String category, String file, String entryId) {
        return enter(category, file, entryId, LoadReport.global()::add);
    }

    /** Opens a scope reporting into {@code sink}. Close it (try-with-resources) when the entry is done. */
    public static LoadScope enter(String category, String file, String entryId, DiagnosticSink sink) {
        return new LoadScope(category, file, entryId, null, sink, new Counters()).push();
    }

    public static Optional<LoadScope> current() {
        return Optional.ofNullable(STACK.get().peek());
    }

    /** Number of open scopes on this thread — for tests asserting nothing leaked. */
    public static int depth() {
        return STACK.get().size();
    }

    /**
     * A scope for a location inside this entry: {@code sub("drops").sub("2").sub("item")} →
     * {@code drops[2].item}. Not pushed — pass it around, or {@link #push()} it so DSL compilation
     * inside reports there.
     */
    public LoadScope sub(String segment) {
        String next;
        if (segment == null || segment.isEmpty()) next = path;
        else if (segment.chars().allMatch(Character::isDigit)) next = (path == null ? "" : path) + "[" + segment + "]";
        else next = path == null ? segment : path + "." + segment;
        return new LoadScope(category, file, entryId, next, sink, counters);
    }

    /** Makes this the current scope until {@link #close()}. */
    public LoadScope push() {
        STACK.get().push(this);
        pushed = true;
        return this;
    }

    @Override
    public void close() {
        if (!pushed) return;
        pushed = false;
        Deque<LoadScope> stack = STACK.get();
        // Pop down to (and including) this scope, tolerating a nested one someone forgot to close.
        while (!stack.isEmpty()) {
            if (stack.pop() == this) break;
        }
        if (stack.isEmpty()) STACK.remove();
    }

    public void report(Severity severity, String message, String hint) {
        if (severity == Severity.ERROR) counters.errors++;
        else if (severity == Severity.WARN) counters.warnings++;
        sink.accept(new ConfigDiagnostic(severity, category, file, entryId, path, message, hint));
    }

    public void error(String message) {
        report(Severity.ERROR, message, null);
    }

    public void error(String message, String hint) {
        report(Severity.ERROR, message, hint);
    }

    public void warn(String message) {
        report(Severity.WARN, message, null);
    }

    public void warn(String message, String hint) {
        report(Severity.WARN, message, hint);
    }

    public void info(String message) {
        report(Severity.INFO, message, null);
    }

    /** Records that this entry refers to content {@code id} of {@code kind} (checked after everything loads). */
    public void ref(String kind, String id) {
        RefRecorder recorder = refRecorder;
        if (recorder != null && id != null && !id.isBlank()) recorder.record(kind, id, source());
    }

    /** Declares a name local to this entry (e.g. a GUI loop variable), so it isn't flagged as unknown. */
    public void declareLocal(String name) {
        if (name != null) counters.locals.add(name.toLowerCase());
    }

    public boolean isLocal(String name) {
        return name != null && counters.locals.contains(name.toLowerCase());
    }

    public int errorCount() {
        return counters.errors;
    }

    public int warningCount() {
        return counters.warnings;
    }

    public ConfigSource source() {
        return new ConfigSource(category, file, entryId, path);
    }

    public String category() { return category; }
    public String file() { return file; }
    public String entryId() { return entryId; }
    public String path() { return path; }
}
