package org.nakii.valmora.api.execution;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.TagService;
import org.nakii.valmora.api.scripting.VariableResolver;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the context in which an execution (ability, mechanic, trigger) occurs.
 * Carries all necessary information about the caster, target, location, and parameters.
 *
 * <p><b>Not thread-safe across invocations:</b> an ExecutionContext is strictly transient — scoped
 * to a single mechanic/ability/trigger invocation. Never store one as a field or pass it to another
 * thread after the invocation returns (see CLAUDE.md §7.3 and §7.5).
 */
public interface ExecutionContext {

    /**
     * Backing store for the generic per-invocation key-value attachments (Phase 1.2 —
     * docs/REFACTOR/PROGRESS.md). Keyed by context identity in a weakly-referenced map so any
     * implementer — including test doubles that don't declare a field for it — gets a working,
     * thread-safe {@code get}/{@code set} for free via the default methods below.
     */
    Map<ExecutionContext, ConcurrentHashMap<String, Object>> ATTACHMENTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Returns the entity that casted/triggered this execution.
     * @return Entity caster
     */
    LivingEntity getCaster();

    /**
     * If the caster is a player, returns the player instance.
     * @return the player instance or empty if not a player
     */
    default Optional<Player> getPlayerCaster() {
        return getCaster() instanceof Player ? Optional.of((Player) getCaster()) : Optional.empty();
    }

    /**
     * Returns the target entity of the execution, if any.
     * @return target entity
     */
    Optional<LivingEntity> getTarget();

    /**
     * Returns the execution location.
     */
    Location getLocation();

    /**
     * @return resolver for script variables
     */
    VariableResolver getVariableResolver();

    /**
     * @return service for player tags
     */
    TagService getTagService();

    /**
     * Returns the parameters associated with this specific execution (from YAML).
     * @return parameters section
     */
    ConfigurationSection getParams();

    // Helper methods for typed parameter access with defaults

    default double getDouble(String key, double def) {
        return getParams().getDouble(key, def);
    }

    default int getInt(String key, int def) {
        return getParams().getInt(key, def);
    }

    default String getString(String key, String def) {
        return getParams().getString(key, def);
    }

    default boolean getBoolean(String key, boolean def) {
        return getParams().getBoolean(key, def);
    }

    // Formula-capable parameter access. A param may be a raw number OR a string
    // containing $variables$ and/or math (e.g. "130 + floor($economy.purse$ / 1000000)").
    // String params are evaluated through the expression engine against this context.

    /**
     * Resolves a numeric parameter that may be a literal number or a formula string.
     */
    default double resolveDouble(String key, double def) {
        Object raw = getParams() == null ? null : getParams().get(key);
        if (raw == null) return def;
        if (raw instanceof Number n) return n.doubleValue();
        Object result = org.nakii.valmora.api.ValmoraAPI.getInstance()
                .getScriptModule().getExpressionEvaluator()
                .evaluate(raw.toString(), this);
        if (result instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(result));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Resolves an integer parameter that may be a literal number or a formula string.
     */
    default int resolveInt(String key, int def) {
        return (int) Math.round(resolveDouble(key, def));
    }

    /**
     * Resolves a string parameter, substituting any $variable$ tokens against this context.
     */
    default String resolveString(String key, String def) {
        Object raw = getParams() == null ? null : getParams().get(key);
        if (raw == null) return def;
        return getVariableResolver().resolveTemplate(raw.toString(), this);
    }

    // --- Generic key-value store with parent-child inheritance (Phase 1.2) ---
    // Namespaced keys by convention, e.g. "pet:level", "slayer:target". `set` always writes to the
    // local context; `get` checks the local map first and falls through to the parent chain.

    /**
     * Reads a namespaced attachment, checking this context first and then walking up the parent
     * chain. Returns {@code null} if not found anywhere in the chain.
     */
    @SuppressWarnings("unchecked")
    default <T> T get(String key) {
        ConcurrentHashMap<String, Object> local = ATTACHMENTS.get(this);
        Object value = local != null ? local.get(key) : null;
        if (value != null) {
            return (T) value;
        }
        ExecutionContext parent = getParent();
        return parent != null ? parent.get(key) : null;
    }

    /**
     * Same as {@link #get(String)}, but returns {@code defaultValue} instead of {@code null}.
     */
    default <T> T get(String key, T defaultValue) {
        T value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Writes a namespaced attachment. Always writes to the local context — never to a parent,
     * even if the key currently resolves through inheritance.
     *
     * <p>{@code value == null} is treated as a local removal rather than a {@link
     * ConcurrentHashMap} write (which disallows null values and would throw) — this keeps it safe
     * to pass through possibly-absent lookups (e.g. an item's rarity/id) without a null check at
     * every call site, and is consistent with {@link #get(String)} treating "absent" and "null" the
     * same way.
     */
    default void set(String key, Object value) {
        if (value == null) {
            remove(key);
            return;
        }
        ATTACHMENTS.computeIfAbsent(this, c -> new ConcurrentHashMap<>()).put(key, value);
    }

    /**
     * Removes a namespaced attachment from this context only. Does not affect the parent chain.
     */
    default Object remove(String key) {
        ConcurrentHashMap<String, Object> local = ATTACHMENTS.get(this);
        return local != null ? local.remove(key) : null;
    }

    /**
     * @return true if {@link #get(String)} would resolve a non-null value, local or inherited.
     */
    default boolean has(String key) {
        return get(key) != null;
    }

    /**
     * @return the keys attached directly to this context (does not include parent keys).
     */
    default Set<String> keySet() {
        ConcurrentHashMap<String, Object> local = ATTACHMENTS.get(this);
        return local != null ? Collections.unmodifiableSet(local.keySet()) : Collections.emptySet();
    }

    /**
     * @return the parent context to fall through to on a local miss, or null if this is a root context.
     */
    default ExecutionContext getParent() {
        return null;
    }

    /**
     * Sets the parent context for inheritance. Implementations that want real parent-child nesting
     * (e.g. {@link SimpleExecutionContext}) should override this and {@link #getParent()} to back
     * onto a real field; the default no-ops so existing implementers keep compiling unchanged.
     */
    default void setParent(ExecutionContext parent) {
        // no-op by default
    }
}
