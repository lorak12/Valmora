package org.nakii.valmora.infrastructure.config.diag;

/**
 * Where a piece of content came from — captured from the active {@link LoadScope} when something
 * is compiled, so problems found later (deferred checks, runtime failures) can still name the file
 * and entry. {@link #UNKNOWN} when nothing was being loaded (e.g. a script compiled at runtime).
 */
public record ConfigSource(String category, String file, String entryId, String path) {

    public static final ConfigSource UNKNOWN = new ConfigSource(null, null, null, null);

    public boolean isKnown() {
        return file != null || entryId != null;
    }

    public ConfigDiagnostic diagnostic(Severity severity, String message, String hint) {
        return new ConfigDiagnostic(severity, category, file, entryId, path, message, hint);
    }

    /** {@code file 'entry'} (or {@code "unknown source"}) — for "used in ..." lists. */
    public String describe() {
        if (!isKnown()) return "unknown source";
        StringBuilder sb = new StringBuilder();
        if (file != null) sb.append(file);
        if (entryId != null) sb.append(sb.length() > 0 ? " " : "").append('\'').append(entryId).append('\'');
        if (path != null) sb.append(" › ").append(path);
        return sb.toString();
    }
}
