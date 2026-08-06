package org.nakii.valmora.module.reforge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.Rarity;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code enchant/forge_costs.yml} (Phase 4.4 of the refactor — see
 * docs/REFACTOR/PROGRESS.md), replacing {@link ReforgeModule}'s previously hardcoded
 * {@code RARITY_COST} static {@code EnumMap}. Missing file/entries keep the exact pre-refactor
 * values, so an admin who never creates this file sees no behavior change.
 */
public class ForgeCostRegistry {

    private static final Map<String, Integer> DEFAULTS = Map.of(
            "COMMON", 250,
            "UNCOMMON", 500,
            "RARE", 1000,
            "EPIC", 2500,
            "LEGENDARY", 5000,
            "MYTHIC", 10000,
            "DIVINE", 15000
    );

    private static final int FALLBACK_COST = 250; // matches the old getOrDefault(rarity, 250)

    private final Map<String, Integer> costs = new ConcurrentHashMap<>();

    public void load(Valmora plugin) {
        costs.clear();
        costs.putAll(DEFAULTS);

        File file = new File(plugin.getDataFolder(), "enchant/forge_costs.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("forge_costs");
        if (section == null) return;

        for (String rarity : section.getKeys(false)) {
            costs.put(rarity.toUpperCase(Locale.ROOT), section.getInt(rarity, FALLBACK_COST));
        }
        plugin.getLogger().info("[ForgeCostRegistry] Loaded forge costs for " + costs.size() + " rarities.");
    }

    public int getCost(Rarity rarity) {
        if (rarity == null) return FALLBACK_COST;
        return costs.getOrDefault(rarity.name(), FALLBACK_COST);
    }

    public void clear() {
        costs.clear();
    }
}
