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
        if (id == null) return Optional.empty();
        ModifierGroupDefinition group = groups.get(id.toLowerCase(Locale.ROOT));
        if (group == null) group = groups.get(org.nakii.valmora.infrastructure.versioning.IdAliases.resolve(org.nakii.valmora.infrastructure.versioning.IdAliases.MODIFIER_GROUPS, id));
        return Optional.ofNullable(group);
    }

    public Collection<ModifierGroupDefinition> values() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }
}
