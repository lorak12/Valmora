package org.nakii.valmora.module.recipe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;

/**
 * Loads {@code recipes/anvil_templates.yml} (Phase 4.3 of the refactor — see
 * docs/REFACTOR/PROGRESS.md), replacing {@link AnvilMachineHandler}'s previously hardcoded
 * "10 coins per merged enchant level" cost formula. Missing file/keys keep the exact pre-refactor
 * value (10), so an admin who never creates this file sees no behavior change.
 */
public class AnvilTemplateRegistry {

    private static final int DEFAULT_COST_PER_LEVEL = 10;

    private int mergeCostPerLevel = DEFAULT_COST_PER_LEVEL;

    public void load(Valmora plugin) {
        mergeCostPerLevel = DEFAULT_COST_PER_LEVEL;

        File file = new File(plugin.getDataFolder(), "recipes/anvil_templates.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mergeCostPerLevel = config.getInt("templates.merge.cost-per-level", DEFAULT_COST_PER_LEVEL);
        plugin.getLogger().info("[AnvilTemplateRegistry] merge cost-per-level = " + mergeCostPerLevel);
    }

    public int getMergeCostPerLevel() {
        return mergeCostPerLevel;
    }

    public void clear() {
        mergeCostPerLevel = DEFAULT_COST_PER_LEVEL;
    }
}
