package org.nakii.valmora.module.modifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Id -> {@link ModifierDefinition}, case-insensitive (CLAUDE.md §7.2 Registry convention). */
public class ModifierRegistry {

    private final Map<String, ModifierDefinition> modifiers = new LinkedHashMap<>();

    public void register(ModifierDefinition modifier) {
        modifiers.put(modifier.getId().toLowerCase(Locale.ROOT), modifier);
    }

    public Optional<ModifierDefinition> get(String id) {
        if (id == null) return Optional.empty();
        ModifierDefinition def = modifiers.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            // Renamed modifier (previous-ids): gear still carrying the old id keeps working.
            def = modifiers.get(org.nakii.valmora.infrastructure.versioning.IdAliases.resolve(org.nakii.valmora.infrastructure.versioning.IdAliases.MODIFIERS, id));
        }
        return Optional.ofNullable(def);
    }

    public Collection<ModifierDefinition> values() {
        return modifiers.values();
    }

    public Collection<ModifierDefinition> valuesInGroup(String groupId) {
        return modifiers.values().stream()
                .filter(m -> m.getGroupId().equalsIgnoreCase(groupId))
                .toList();
    }

    public void clear() {
        modifiers.clear();
    }
}
