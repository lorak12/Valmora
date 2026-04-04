package org.nakii.valmora.api.execution;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
     * Returns the location where the execution is occurring.
     * @return execution location
     */
    Location getLocation();

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
}
