package org.nakii.valmora.module.mob;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry-backed mob category tag, replacing the old {@code enum MobCategory} (Phase 3.4 of
 * the refactor — see docs/REFACTOR/PROGRESS.md). Purely a classification label attached to a
 * {@link MobDefinition} (via {@code category:} in {@code mobs/*.yml}) and consulted by
 * {@code SkillDefinition}'s per-category XP bonus lookup — it carries no behavior of its own,
 * unlike {@link org.nakii.valmora.module.combat.DamageType}.
 *
 * <p>Kept API-compatible with the old enum for the same reason {@code DamageType} was: existing
 * call sites (`MobCategory.valueOf(...)`, `MobCategory.UNDEAD`, `.name()`) needed zero changes.
 * New categories can be registered via {@code mob_categories.yml} without a code change/recompile.
 */
public final class MobCategory {

    private static final java.util.Map<String, MobCategory> REGISTRY = new ConcurrentHashMap<>();
    /** Ids defined in code (the constants below); everything else came from YAML. */
    private static final java.util.Set<String> BUILTINS = ConcurrentHashMap.newKeySet();

    public static final MobCategory UNDEAD = define("UNDEAD");
    public static final MobCategory ENDER = define("ENDER");
    public static final MobCategory NETHER = define("NETHER");
    public static final MobCategory BEAST = define("BEAST");
    public static final MobCategory AQUATIC = define("AQUATIC");
    public static final MobCategory ARTHROPOD = define("ARTHROPOD");
    public static final MobCategory ILLAGER = define("ILLAGER");
    public static final MobCategory GOLEM = define("GOLEM");
    public static final MobCategory BOSS = define("BOSS");
    public static final MobCategory OTHER = define("OTHER");

    static {
        BUILTINS.addAll(REGISTRY.keySet());
    }

    /**
     * Drops every YAML-defined entry, keeping only the built-in constants. Called by the loader
     * before (re)loading, so an entry removed from YAML disappears on reload instead of lingering
     * until restart.
     */
    public static void resetToBuiltins() {
        REGISTRY.keySet().retainAll(BUILTINS);
    }

    private final String id;

    private MobCategory(String id) {
        this.id = id;
    }

    /** Registers a category id if it doesn't already exist. Redefining an existing id is a safe no-op (same instance). */
    public static MobCategory define(String id) {
        return REGISTRY.computeIfAbsent(id.toUpperCase(Locale.ROOT), MobCategory::new);
    }

    /** Case-insensitive lookup, throwing like {@code Enum.valueOf} did for backward compatibility. */
    public static MobCategory valueOf(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown mob category: " + id));
    }

    public static Optional<MobCategory> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(id.toUpperCase(Locale.ROOT)));
    }

    public static Collection<MobCategory> values() {
        return List.copyOf(REGISTRY.values());
    }

    public String getId() {
        return id;
    }

    public String name() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
