package org.nakii.valmora.infrastructure.versioning;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Old-id → current-id aliases per content type, so renaming content doesn't orphan everything
 * that already points at it (item and pet ids on existing items, skill/quest/collection keys in
 * profiles, modifier and enchant ids on gear, mob ids on live entities, ...).
 *
 * <p>Sources of aliases:
 * <ul>
 *   <li>{@code previous-ids: [old_id, ...]} on any definition loaded through
 *       {@link org.nakii.valmora.infrastructure.config.YamlLoader} (registered automatically,
 *       keyed by the loader's folder name, e.g. {@code "items"});</li>
 *   <li>content-pack namespacing: the bare id is an alias of {@code <pack>:<id>}, so data written
 *       before the pack was namespaced (or after the pack ledger was lost) still resolves.</li>
 * </ul>
 *
 * <p>An alias claimed by two different ids is ambiguous. It is dropped and logged rather than
 * resolved arbitrarily. Aliases never shadow a real id: {@link #resolve} returns the id unchanged
 * when it is itself canonical, so resolution must go "registry miss → try alias".
 */
public final class IdAliases {

    public static final String ITEMS = "items";
    public static final String MOBS = "mobs";
    public static final String SKILLS = "skills";
    public static final String QUESTS = "quests";
    public static final String COLLECTIONS = "collections";
    public static final String PETS = "pets";
    public static final String ENCHANTS = "enchants";
    public static final String MODIFIERS = "modifiers/definitions";
    public static final String MODIFIER_GROUPS = "modifiers/groups";
    public static final String PROGRESSION = "progression";
    public static final String WARPS = "warps";

    private static final Map<String, Map<String, String>> ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> AMBIGUOUS = new ConcurrentHashMap<>();
    private static volatile Logger logger;

    private IdAliases() {}

    public static void setLogger(Logger log) {
        logger = log;
    }

    /** Forgets every alias of {@code type}; called when that content type starts (re)loading. */
    public static void clear(String type) {
        ALIASES.remove(key(type));
        AMBIGUOUS.remove(key(type));
    }

    /** Registers {@code alias} → {@code canonical} for {@code type} (both case-insensitive). */
    public static void register(String type, String alias, String canonical) {
        if (alias == null || canonical == null) return;
        String a = alias.toLowerCase(), c = canonical.toLowerCase();
        if (a.equals(c)) return;
        Set<String> ambiguous = AMBIGUOUS.computeIfAbsent(key(type), k -> ConcurrentHashMap.newKeySet());
        if (ambiguous.contains(a)) return;
        Map<String, String> map = ALIASES.computeIfAbsent(key(type), k -> new ConcurrentHashMap<>());
        String existing = map.putIfAbsent(a, c);
        if (existing != null && !existing.equals(c)) {
            map.remove(a);
            ambiguous.add(a);
            Logger log = logger;
            if (log != null) {
                log.warning("[" + type + "] Id alias '" + alias + "' is claimed by both '" + existing + "' and '"
                        + canonical + "' — ignoring it. Data still using '" + alias + "' won't resolve until one "
                        + "of them drops it from previous-ids.");
            }
        }
    }

    /** Registers every entry of {@code previousIds} as an alias of {@code canonical}. */
    public static void registerAll(String type, List<String> previousIds, String canonical) {
        if (previousIds == null) return;
        for (String alias : previousIds) register(type, alias, canonical);
    }

    /**
     * Returns the current id for {@code id}: its alias target if {@code id} is a known alias, else
     * {@code id} itself (lowercased). Callers should only consult this after a direct registry
     * lookup misses.
     */
    public static String resolve(String type, String id) {
        if (id == null) return null;
        String lower = id.toLowerCase();
        Map<String, String> map = ALIASES.get(key(type));
        if (map == null) return lower;
        String target = map.get(lower);
        return target != null ? target : lower;
    }

    /** Whether {@code id} is a registered alias of some other id of {@code type}. */
    public static boolean isAlias(String type, String id) {
        Map<String, String> map = ALIASES.get(key(type));
        return id != null && map != null && map.containsKey(id.toLowerCase());
    }

    /** Read-only view of {@code type}'s aliases (alias → canonical), for diagnostics. */
    public static Map<String, String> view(String type) {
        Map<String, String> map = ALIASES.get(key(type));
        return map == null ? Map.of() : Collections.unmodifiableMap(map);
    }

    private static String key(String type) {
        return type.toLowerCase();
    }
}
