package org.nakii.valmora.api.execution;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.TagService;
import org.nakii.valmora.api.scripting.VariableResolver;

import java.util.Optional;

/**
 * Represents the context in which an execution (ability, mechanic, trigger) occurs.
 * Carries all necessary information about the caster, target, location, and parameters.
 */
public interface ExecutionContext {

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
}
