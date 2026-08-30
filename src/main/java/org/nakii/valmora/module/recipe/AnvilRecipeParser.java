package org.nakii.valmora.module.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.config.LoadResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parses one {@code recipes/anvil/*.yml} top-level entry into an {@link AnvilRecipeDefinition}
 *  (coworker anvil spec §5 YAML schema — {@code type: UPGRADE} / {@code type: TRANSMUTE}). */
public class AnvilRecipeParser {

    public static LoadResult<AnvilRecipeDefinition, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            // Only anvil UPGRADE/TRANSMUTE entries live in this loader — anything else (or a
            // machine other than "anvil") isn't for us; RecipeModule skips a null-typed result.
            if (!"anvil".equalsIgnoreCase(section.getString("machine", ""))) return LoadResult.success(null);
            String typeStr = section.getString("type", "");
            AnvilRecipeDefinition.Type type;
            try {
                type = AnvilRecipeDefinition.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return LoadResult.success(null); // not one of ours (e.g. a future anvil recipe kind)
            }

            AnvilRecipeDefinition.ItemMatch base = parseMatch(section.getConfigurationSection("base"));
            if (base == null) {
                return LoadResult.failure("[" + filePath + "] Anvil recipe " + id + " is missing a base: block.");
            }
            AnvilRecipeDefinition.ItemMatch addition = parseMatch(section.getConfigurationSection("addition"));

            ConfigurationSection resultSec = section.getConfigurationSection("result");
            if (resultSec == null) {
                return LoadResult.failure("[" + filePath + "] Anvil recipe " + id + " is missing a result: block.");
            }
            String resultMaterial = resultSec.getString("material");
            String resultValmoraId = resultSec.getString("valmora_id");
            String resultName = resultSec.getString("name", null);
            List<String> addLore = resultSec.getStringList("add_lore");

            Map<String, Integer> addEnchants = new HashMap<>();
            ConfigurationSection enchSec = resultSec.getConfigurationSection("add_enchants");
            if (enchSec != null) {
                for (String key : enchSec.getKeys(false)) addEnchants.put(key, enchSec.getInt(key));
            }

            Map<String, Object> addNbt = new HashMap<>();
            ConfigurationSection nbtSec = resultSec.getConfigurationSection("add_nbt");
            if (nbtSec != null) {
                for (String key : nbtSec.getKeys(false)) addNbt.put(key, nbtSec.get(key));
            }

            AnvilRecipeDefinition.ResultSpec result = new AnvilRecipeDefinition.ResultSpec(
                    resultMaterial, resultValmoraId, resultName, addLore, addEnchants, addNbt);

            boolean keepDataOnUpgrade = section.getBoolean("keep-data-on-upgrade", true);
            boolean increaseWorkPenalty = resultSec.getBoolean("increase_work_penalty", true);

            ConfigurationSection costSec = section.getConfigurationSection("cost");
            int costXpLevels = costSec != null ? costSec.getInt("xp_levels", 0) : 0;
            int costCoins = costSec != null ? costSec.getInt("coins", 0) : 0;

            AnvilRecipeDefinition def = new AnvilRecipeDefinition(id, type, base, addition, result,
                    keepDataOnUpgrade, increaseWorkPenalty, costXpLevels, costCoins);
            return LoadResult.success(def);
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Error parsing anvil recipe " + id + ": " + e.getMessage());
        }
    }

    private static AnvilRecipeDefinition.ItemMatch parseMatch(ConfigurationSection section) {
        if (section == null) return null;
        String material = section.getString("material", null);
        String valmoraId = section.getString("valmora_id", null);
        if (material == null && valmoraId == null) return null;
        int amount = section.getInt("amount", 1);
        return new AnvilRecipeDefinition.ItemMatch(material, valmoraId, amount);
    }
}
