package org.nakii.valmora.module.item.set;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and stores armor {@link SetBonusDefinition}s from the {@code set_bonuses/} folder.
 * Keys are set ids (case-insensitive, stored lowercase).
 */
public class SetBonusRegistry {

    private final Valmora plugin;
    private final Map<String, SetBonusDefinition> registry = new HashMap<>();

    public SetBonusRegistry(Valmora plugin) {
        this.plugin = plugin;
    }

    public void load() {
        registry.clear();
        YamlLoader<SetBonusDefinition> loader = new YamlLoader<SetBonusDefinition>(plugin, "set_bonuses", "Set bonuses").kind(org.nakii.valmora.infrastructure.config.refs.Kinds.SET_BONUS);
        loader.load(SetBonusParser::parse, def -> registry.put(def.setId().toLowerCase(), def));
    }

    public Optional<SetBonusDefinition> get(String setId) {
        if (setId == null) return Optional.empty();
        return Optional.ofNullable(registry.get(setId.toLowerCase()));
    }

    public void clear() {
        registry.clear();
    }

    /** Every loaded set id. */
    public java.util.Set<String> ids() {
        return java.util.Set.copyOf(registry.keySet());
    }
}
