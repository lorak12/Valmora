package org.nakii.valmora.infrastructure.config.refs;

import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every reference content made to other content during loading ({@code LoadScope.ref(kind, id)}).
 * A folder's references are dropped whenever that folder starts reloading, so a partial reload
 * never leaves stale ones behind.
 */
public final class ReferenceIndex {

    private static final ReferenceIndex GLOBAL = new ReferenceIndex();

    public static ReferenceIndex global() {
        return GLOBAL;
    }

    /** While set on a thread, references recorded through {@link #global()} go here instead (dry runs). */
    private static final ThreadLocal<ReferenceIndex> REDIRECT = new ThreadLocal<>();

    private final Set<ContentRef> refs = new LinkedHashSet<>();

    /** Runs {@code action} with every reference recorded on this thread captured into {@code scratch}. */
    public static void capturing(ReferenceIndex scratch, Runnable action) {
        ReferenceIndex previous = REDIRECT.get();
        REDIRECT.set(scratch);
        try {
            action.run();
        } finally {
            if (previous == null) REDIRECT.remove();
            else REDIRECT.set(previous);
        }
    }

    public void record(String kind, String id, ConfigSource source) {
        ReferenceIndex target = REDIRECT.get();
        (target != null && this == GLOBAL ? target : this).recordHere(kind, id, source);
    }

    private synchronized void recordHere(String kind, String id, ConfigSource source) {
        if (kind == null || id == null || id.isBlank()) return;
        refs.add(new ContentRef(kind.toLowerCase(Locale.ROOT), id.trim(), source == null ? ConfigSource.UNKNOWN : source));
    }

    /** Drops every reference whose source file is {@code prefix} or lives under the {@code prefix/} folder. */
    public synchronized void clearFiles(String prefix) {
        if (prefix == null) return;
        String folder = prefix.endsWith("/") ? prefix : prefix + "/";
        refs.removeIf(r -> r.source().file() != null
                && (r.source().file().equals(prefix) || r.source().file().startsWith(folder)));
    }

    public synchronized List<ContentRef> all() {
        return new ArrayList<>(refs);
    }

    public synchronized int size() {
        return refs.size();
    }

    public synchronized void clear() {
        refs.clear();
    }

    /** Hooks the index into the loaders: {@code LoadScope.ref} records here, and a folder's refs clear when it reloads. */
    public static void install() {
        LoadScope.setRefRecorder(GLOBAL::record);
        org.nakii.valmora.infrastructure.config.YamlLoader.setLoadStartListener(GLOBAL::clearFiles);
    }
}
