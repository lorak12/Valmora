package org.nakii.valmora.infrastructure.config.diag;

/**
 * One problem found while loading content: which file and entry it belongs to, where inside the
 * entry ({@code path}, e.g. {@code drops[2].item}), what is wrong and an optional hint (a "did you
 * mean" suggestion, or "kept the previous version").
 *
 * <p>Every field except {@code severity} and {@code message} may be null.
 */
public record ConfigDiagnostic(Severity severity, String category, String file, String entryId,
                               String path, String message, String hint) {

    public ConfigDiagnostic {
        if (severity == null) severity = Severity.ERROR;
        if (message == null) message = "unknown problem";
    }

    public ConfigDiagnostic withHint(String newHint) {
        return new ConfigDiagnostic(severity, category, file, entryId, path, message, newHint);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Wraps an old-style free-form error string ({@code "[file] ..."} as returned by a parser's
     * {@code LoadResult.failure}). The {@code [file]} prefix is stripped so it isn't printed twice,
     * and the entry id is dropped when the message already names it, so {@link #format()} gives back
     * exactly the original string.
     */
    public static ConfigDiagnostic legacy(Severity severity, String category, String file, String entryId, String raw) {
        String message = raw == null ? "unknown problem" : raw;
        if (file != null && message.startsWith("[" + file + "] ")) {
            message = message.substring(file.length() + 3);
        } else if (message.startsWith("[") && message.indexOf("] ") > 0) {
            // Prefixed with some other path (e.g. a qualified or nested file) — keep it verbatim.
            return new ConfigDiagnostic(severity, category, null, null, null, message, null);
        }
        String entry = entryId != null && message.contains(stripNamespace(entryId)) ? null : entryId;
        return new ConfigDiagnostic(severity, category, file, entry, null, message, null);
    }

    private static String stripNamespace(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** {@code [file] 'entry' › path: message (hint)} — the single-line form used in logs and reports. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (file != null) sb.append('[').append(file).append("] ");
        if (entryId != null) {
            sb.append('\'').append(entryId).append('\'');
            sb.append(path != null ? " › " + path + ": " : ": ");
        } else if (path != null) {
            sb.append(path).append(": ");
        }
        sb.append(message);
        if (hint != null && !hint.isEmpty()) sb.append(" (").append(hint).append(')');
        return sb.toString();
    }

    @Override
    public String toString() {
        return severity + " " + format();
    }
}
