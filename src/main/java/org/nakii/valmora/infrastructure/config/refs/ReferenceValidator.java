package org.nakii.valmora.infrastructure.config.refs;

import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.infrastructure.config.diag.DiagnosticSink;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs once all content is loaded: checks that every recorded reference ({@link ReferenceIndex})
 * points at content that exists ({@link ContentIndex}), then runs every registered
 * {@link ReferenceCheck}. Everything it finds is a warning — the referencing content stays loaded.
 */
public final class ReferenceValidator {

    private static final ReferenceValidator GLOBAL = new ReferenceValidator(ContentIndex.global(), ReferenceIndex.global());

    public static ReferenceValidator global() {
        return GLOBAL;
    }

    private final ContentIndex index;
    private final ReferenceIndex refs;
    private final Map<String, ReferenceCheck> checks = new LinkedHashMap<>();

    public ReferenceValidator(ContentIndex index, ReferenceIndex refs) {
        this.index = index;
        this.refs = refs;
    }

    /** Registers (or replaces, by name) a check. Modules call this from {@code onEnable()}. */
    public synchronized void register(ReferenceCheck check) {
        checks.put(check.name(), check);
    }

    public synchronized void unregister(String name) {
        checks.remove(name);
    }

    public void runAll(DiagnosticSink sink) {
        runAll(sink, null);
    }

    /**
     * @param pendingIds ids defined on disk but not loaded yet ({@code /valmora validate}), by kind —
     *                   accepted as existing
     */
    public void runAll(DiagnosticSink sink, Map<String, Set<String>> pendingIds) {
        ReferenceContext context = new ReferenceContext(index, refs, sink, pendingIds);
        checkDangling(context);
        Map<String, ReferenceCheck> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>(checks);
        }
        for (ReferenceCheck check : snapshot.values()) {
            try {
                check.check(context);
            } catch (RuntimeException e) {
                Logger.getLogger("Valmora").log(Level.WARNING, "Reference check '" + check.name() + "' failed", e);
            }
        }
    }

    /** Only the dangling-reference check (no registered checks) — for dry runs over a scratch index. */
    public void runDanglingOnly(DiagnosticSink sink, Map<String, Set<String>> pendingIds) {
        checkDangling(new ReferenceContext(index, refs, sink, pendingIds));
    }

    /** Every recorded reference of a registered kind must resolve. */
    void checkDangling(ReferenceContext context) {
        Set<String> reported = new HashSet<>();
        for (ContentRef ref : refs.all()) {
            if (!index.isKnownKind(ref.kind())) continue;
            if (resolves(context, ref)) continue;
            String dedupe = ref.kind() + "|" + ref.id().toLowerCase() + "|" + ref.source().file() + "|" + ref.source().entryId() + "|" + ref.source().path();
            if (!reported.add(dedupe)) continue;
            context.warn(ref.source(), "unknown " + ref.kind().replace('_', ' ') + " '" + ref.id() + "'",
                    Suggestions.hint(ref.id(), index.ids(ref.kind())));
        }
    }

    private boolean resolves(ReferenceContext context, ContentRef ref) {
        String id = ref.id();
        if (context.exists(ref.kind(), id)) return true;
        // Bare ids inside a content pack resolve to that pack's namespace, as the loaders do.
        if (ref.source().file() != null && id.indexOf(':') < 0) {
            String qualified = YamlLoader.qualify(id, ref.source().file());
            if (!qualified.equals(id) && context.exists(ref.kind(), qualified)) return true;
        }
        return false;
    }
}
