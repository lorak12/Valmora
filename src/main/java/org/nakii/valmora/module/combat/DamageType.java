package org.nakii.valmora.module.combat;

import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry-backed damage type, replacing the old {@code enum DamageType} (Phase 2.1 of the
 * refactor — see docs/REFACTOR/PROGRESS.md and docs/REFACTOR_BLUEPRINT.md §2.2/§3 Phase 2).
 *
 * <p>Deliberately kept API-compatible with the old enum so every existing call site
 * (`DamageType.MELEE`, `DamageType.valueOf(...)`, `==` comparisons, switch-producing code) keeps
 * compiling unchanged:
 * <ul>
 *   <li>{@code MELEE}, {@code FIRE}, etc. are still public static fields, resolved once at class
 *       init and mutated in place by {@link #define} — so identity (and therefore {@code ==}) is
 *       stable across a {@code /valmora reload} even though the backing data can change.</li>
 *   <li>{@link #valueOf(String)} still throws {@link IllegalArgumentException} on an unknown id,
 *       matching {@code Enum.valueOf}'s contract.</li>
 * </ul>
 * New behavior server admins can now configure via {@code damage_types/*.yml} without a code
 * change: {@code color}, whether the type {@link #isIgnoresDefense() ignores victim defense}
 * (replacing the old hardcoded {@code VOID}/{@code DROWNING}/{@code FALL} exclusion list in
 * {@code DamageCalculator}), and a list of on-hit script events pre-compiled once at load time.
 */
public final class DamageType {

    private static final Map<String, DamageType> REGISTRY = new ConcurrentHashMap<>();
    /** Ids defined in code (the constants below); everything else came from YAML. */
    private static final java.util.Set<String> BUILTINS = ConcurrentHashMap.newKeySet();

    // Backward-compatible default instances — always present even before any YAML is loaded, and
    // overwritten in place (not replaced) by damage_types/*.yml if the admin redefines them.
    public static final DamageType MELEE = define("MELEE", "<white>", false, List.of());
    public static final DamageType PROJECTILE = define("PROJECTILE", "<gray>", false, List.of());
    public static final DamageType FALL = define("FALL", "<dark_gray>", true, List.of());
    public static final DamageType DROWNING = define("DROWNING", "<blue>", true, List.of());
    public static final DamageType FIRE = define("FIRE", "<#FF8C00>", false, List.of());
    public static final DamageType LAVA = define("LAVA", "<dark_red>", false, List.of());
    public static final DamageType MAGIC = define("MAGIC", "<aqua>", false, List.of());
    public static final DamageType VOID = define("VOID", "<black>", true, List.of());
    public static final DamageType POISON = define("POISON", "<green>", false, List.of());
    public static final DamageType WITHER = define("WITHER", "<black>", false, List.of());
    public static final DamageType EXPLOSION = define("EXPLOSION", "<red>", false, List.of());
    public static final DamageType SUICIDE = define("SUICIDE", "<black>", true, List.of());
    public static final DamageType CONTACT = define("CONTACT", "<green>", false, List.of());
    public static final DamageType STARVATION = define("STARVATION", "<gold>", true, List.of());
    public static final DamageType DRAGON_BREATH = define("DRAGON_BREATH", "<light_purple>", false, List.of());
    public static final DamageType SONIC_BOOM = define("SONIC_BOOM", "<aqua>", true, List.of());
    public static final DamageType OUTSIDE_BORDER = define("OUTSIDE_BORDER", "<black>", true, List.of());
    // VANILLA_CONTROL_AUDIT.md §7/§14 gap fix: LIGHTNING and FREEZE previously had no switch case in
    // CombatListener.mapCauseToType and silently fell through to the MELEE fallback.
    public static final DamageType LIGHTNING = define("LIGHTNING", "<yellow>", false, List.of());
    public static final DamageType FREEZE = define("FREEZE", "<aqua>", false, List.of());

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
    private volatile String color;
    private volatile boolean ignoresDefense;
    private volatile List<String> onHitEventStrings = List.of();
    private volatile CompiledEvent compiledOnHit = context -> {};

    private DamageType(String id) {
        this.id = id;
    }

    /**
     * Registers or redefines a damage type. Reuses the existing instance for a given id if one
     * exists (so `==` identity is stable across reloads), otherwise creates a new singleton.
     * On-hit events are pre-compiled here (once, at load time) — never re-parsed on the hot path.
     */
    public static DamageType define(String id, String color, boolean ignoresDefense, List<String> onHitEvents) {
        String key = id.toUpperCase(Locale.ROOT);
        DamageType type = REGISTRY.computeIfAbsent(key, DamageType::new);
        type.color = color;
        type.ignoresDefense = ignoresDefense;
        type.onHitEventStrings = onHitEvents == null ? List.of() : List.copyOf(onHitEvents);
        type.compiledOnHit = type.onHitEventStrings.isEmpty()
                ? context -> {}
                : org.nakii.valmora.api.ValmoraAPI.getInstance().getScriptModule().getEventParser().parseList(type.onHitEventStrings);
        return type;
    }

    /** Case-insensitive lookup, throwing like {@code Enum.valueOf} did for backward compatibility. */
    public static DamageType valueOf(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown damage type: " + id));
    }

    public static Optional<DamageType> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(id.toUpperCase(Locale.ROOT)));
    }

    public static Collection<DamageType> values() {
        return List.copyOf(REGISTRY.values());
    }

    public String getId() {
        return id;
    }

    /** Kept for any code that still calls {@code .name()} out of enum habit. */
    public String name() {
        return id;
    }

    public String getColor() {
        return color;
    }

    /**
     * Replaces the old hardcoded {@code damageType != VOID && != DROWNING && != FALL} chain in
     * {@code DamageCalculator} — true means victim defense is never applied against this type.
     */
    public boolean isIgnoresDefense() {
        return ignoresDefense;
    }

    public List<String> getOnHitEventStrings() {
        return onHitEventStrings;
    }

    /** Fires this type's pre-compiled on-hit script events (no-op if none were configured). */
    public void fireOnHit(org.nakii.valmora.api.execution.ExecutionContext context) {
        compiledOnHit.execute(context);
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
