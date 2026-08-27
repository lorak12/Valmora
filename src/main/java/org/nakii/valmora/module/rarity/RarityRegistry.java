package org.nakii.valmora.module.rarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup by stable id/key and ordered rank for {@link RarityDefinition}s loaded from
 * {@code rarities.yml}. See docs/Valmora_Modifier_Framework_Design.docx §4/§21.
 */
public class RarityRegistry {

    private final Map<String, RarityDefinition> byKey = new LinkedHashMap<>();
    private final Map<String, RarityDefinition> byId = new LinkedHashMap<>();

    public void clear() {
        byKey.clear();
        byId.clear();
    }

    public void register(RarityDefinition def) {
        byKey.put(def.getKey().toUpperCase(Locale.ROOT), def);
        byId.put(def.getId().toLowerCase(Locale.ROOT), def);
    }

    /** Looks up by registry key (enum-constant-style, e.g. {@code "LEGENDARY"}) — case-insensitive. */
    public Optional<RarityDefinition> getByKey(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(byKey.get(key.toUpperCase(Locale.ROOT)));
    }

    /** Looks up by stable id (e.g. {@code "legendary"}) — case-insensitive. */
    public Optional<RarityDefinition> getById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    /** All rarities ordered by ascending rank. */
    public List<RarityDefinition> getOrdered() {
        List<RarityDefinition> list = new ArrayList<>(byKey.values());
        list.sort(Comparator.comparingInt(RarityDefinition::getRank));
        return list;
    }

    public Collection<RarityDefinition> values() {
        return byKey.values();
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }
}
