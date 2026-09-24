package org.nakii.valmora.infrastructure.config.refs;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What content exists, by kind ({@code item}, {@code mob}, {@code gui}, ...). Each module
 * registers the kinds it owns in {@code onEnable()}; the {@link ReferenceValidator} checks every
 * recorded reference against it once everything has loaded.
 *
 * <p>Kinds are plain lowercase strings (see {@link Kinds} for the common ones) so add-ons can add
 * their own.
 */
public final class ContentIndex {

    private record Kind(Predicate<String> exists, Supplier<Collection<String>> ids) {}

    private static final ContentIndex GLOBAL = new ContentIndex();

    public static ContentIndex global() {
        return GLOBAL;
    }

    private final Map<String, Kind> kinds = new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) a kind.
     * @param exists whether an id of this kind exists — should accept any case and resolve aliases
     * @param ids every known id, for "did you mean" suggestions
     */
    public void register(String kind, Predicate<String> exists, Supplier<Collection<String>> ids) {
        kinds.put(kind.toLowerCase(Locale.ROOT), new Kind(exists, ids));
    }

    public void unregister(String kind) {
        kinds.remove(kind.toLowerCase(Locale.ROOT));
    }

    public boolean isKnownKind(String kind) {
        return kind != null && kinds.containsKey(kind.toLowerCase(Locale.ROOT));
    }

    public Set<String> kinds() {
        return Set.copyOf(kinds.keySet());
    }

    public boolean exists(String kind, String id) {
        Kind k = kind == null ? null : kinds.get(kind.toLowerCase(Locale.ROOT));
        if (k == null || id == null) return false;
        try {
            return k.exists().test(id);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Collection<String> ids(String kind) {
        Kind k = kind == null ? null : kinds.get(kind.toLowerCase(Locale.ROOT));
        if (k == null) return List.of();
        try {
            Collection<String> ids = k.ids().get();
            return ids == null ? List.of() : ids;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** For tests. */
    public void clear() {
        kinds.clear();
    }
}
