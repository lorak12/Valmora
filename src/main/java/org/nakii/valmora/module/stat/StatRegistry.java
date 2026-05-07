package org.nakii.valmora.module.stat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

public class StatRegistry {

    private final LinkedHashMap<String, StatDefinition> stats = new LinkedHashMap<>();

    public void register(StatDefinition def) {
        stats.put(def.getId().toLowerCase(), def);
    }

    public Optional<StatDefinition> get(String id) {
        return Optional.ofNullable(stats.get(id.toLowerCase()));
    }

    public Collection<StatDefinition> values() {
        return stats.values();
    }

    public Set<String> getKeys() {
        return stats.keySet();
    }

    public boolean contains(String id) {
        return stats.containsKey(id.toLowerCase());
    }

    public void clear() {
        stats.clear();
    }
}
