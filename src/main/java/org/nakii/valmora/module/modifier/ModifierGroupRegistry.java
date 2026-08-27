package org.nakii.valmora.module.modifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Id -> {@link ModifierGroupDefinition}, case-insensitive (CLAUDE.md §7.2 Registry convention). */
public class ModifierGroupRegistry {

    private final Map<String, ModifierGroupDefinition> groups = new LinkedHashMap<>();

    public void register(ModifierGroupDefinition group) {
        groups.put(group.getId().toLowerCase(Locale.ROOT), group);
    }

    public Optional<ModifierGroupDefinition> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(groups.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<ModifierGroupDefinition> values() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }
}
