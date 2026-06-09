package org.nakii.valmora.module.item;

import org.bukkit.configuration.MemoryConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared parser for a YAML "mechanics:" list (a list of maps with a {@code type} and optional
 * {@code params}). Used by both item abilities ({@link ItemDefinitionParser}) and mob/boss
 * abilities so the two stay in sync.
 */
public final class MechanicParser {

    private MechanicParser() {}

    /** Thrown when a mechanic references an unknown {@code type}. */
    public static class UnknownMechanicException extends Exception {
        public UnknownMechanicException(String message) {
            super(message);
        }
    }

    /**
     * Parses a list of mechanic maps into {@link ConfiguredMechanic} instances.
     *
     * @param mechanicMaps the raw {@code getMapList("mechanics")} value
     * @param registry     the mechanic registry to resolve {@code type} against
     * @return the configured mechanics, in order
     * @throws UnknownMechanicException if a {@code type} is not registered
     */
    public static List<ConfiguredMechanic> parse(List<Map<?, ?>> mechanicMaps, MechanicRegistry registry)
            throws UnknownMechanicException {
        java.util.List<ConfiguredMechanic> result = new java.util.ArrayList<>();
        for (Map<?, ?> map : mechanicMaps) {
            Object typeObj = map.get("type");
            if (typeObj == null) continue;
            String type = typeObj.toString();

            Optional<AbilityMechanic> mechOpt = registry.getMechanic(type);
            if (mechOpt.isEmpty()) {
                throw new UnknownMechanicException(type);
            }

            // Convert the "params" map into a ConfigurationSection for easy Java reading
            MemoryConfiguration params = new MemoryConfiguration();
            if (map.get("params") instanceof Map<?, ?> paramsMap) {
                for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
                    params.set(entry.getKey().toString(), entry.getValue());
                }
            }
            result.add(new ConfiguredMechanic(mechOpt.get(), params));
        }
        return result;
    }
}
