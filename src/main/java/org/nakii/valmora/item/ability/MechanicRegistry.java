package org.nakii.valmora.item.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MechanicRegistry {
    private final Map<String, AbilityMechanic> registry = new HashMap<>();

    public void registerMechanic(AbilityMechanic mechanic) {
        registry.put(mechanic.getId().toUpperCase(), mechanic);
    }

    public Optional<AbilityMechanic> getMechanic(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id.toUpperCase()));
    }

    public void clear() {
        registry.clear();
    }

    public Map<String, AbilityMechanic> getRegistry() {
        return registry;
    }

    public int size() {
        return registry.size();
    }

}
