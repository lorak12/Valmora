package org.nakii.valmora.infrastructure.config.refs;

import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.DiagnosticSink;
import org.nakii.valmora.infrastructure.config.diag.Severity;

import java.util.Map;
import java.util.Set;

/** What a {@link ReferenceCheck} gets: the content that exists, the references recorded, and where to report. */
public final class ReferenceContext {

    private final ContentIndex index;
    private final ReferenceIndex refs;
    private final DiagnosticSink sink;
    private final Map<String, Set<String>> pendingIds;

    ReferenceContext(ContentIndex index, ReferenceIndex refs, DiagnosticSink sink, Map<String, Set<String>> pendingIds) {
        this.index = index;
        this.refs = refs;
        this.sink = sink;
        this.pendingIds = pendingIds == null ? Map.of() : pendingIds;
    }

    public ContentIndex index() {
        return index;
    }

    public ReferenceIndex refs() {
        return refs;
    }

    /**
     * Whether {@code id} of {@code kind} exists — live, or (during {@code /valmora validate}) defined
     * on disk but not loaded yet.
     */
    public boolean exists(String kind, String id) {
        if (index.exists(kind, id)) return true;
        Set<String> pending = pendingIds.get(kind);
        return pending != null && id != null && pending.contains(id.toLowerCase());
    }

    public void warn(ConfigSource source, String message, String hint) {
        report(source, Severity.WARN, message, hint);
    }

    public void report(ConfigSource source, Severity severity, String message, String hint) {
        ConfigSource s = source == null ? ConfigSource.UNKNOWN : source;
        sink.accept(new ConfigDiagnostic(severity, s.category() == null ? "References" : s.category(),
                s.file(), s.entryId(), s.path(), message, hint));
    }

    /** Reports a problem not tied to one file, under the given category. */
    public void warn(String category, String file, String entryId, String message, String hint) {
        sink.accept(new ConfigDiagnostic(Severity.WARN, category, file, entryId, null, message, hint));
    }
}
