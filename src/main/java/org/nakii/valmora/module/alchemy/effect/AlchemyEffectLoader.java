package org.nakii.valmora.module.alchemy.effect;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlchemyEffectLoader {

    public static YamlLoader.SectionParser<AlchemyEffect> parser() {
        return AlchemyEffectLoader::parse;
    }

    /** Converts a YAML ingredient string to a canonical key used in AlchemyManager's index. */
    private static String resolveIngredientKey(String ingStr) {
        // If already namespaced (e.g. "minecraft:sugar"), resolve the material and normalise
        if (ingStr.startsWith("minecraft:")) {
            return ingStr.toLowerCase();
        }
        // Try vanilla material lookup
        Material mat = Material.matchMaterial(ingStr.toUpperCase());
        if (mat != null) {
            return "minecraft:" + mat.name().toLowerCase();
        }
        // Custom Valmora item ID
        return ingStr.toLowerCase();
    }

    private static LoadResult<AlchemyEffect, String> parse(String id, ConfigurationSection s, String path) {
        try {
            String name = s.getString("name", id);
            String typeStr = s.getString("type", "BUFF").toUpperCase();
            AlchemyEffectType type = AlchemyEffectType.valueOf(typeStr);
            String rarity = s.getString("rarity", "COMMON").toUpperCase();

            Color color = Color.PURPLE;
            String colorHex = s.getString("color");
            if (colorHex != null) {
                colorHex = colorHex.replace("#", "");
                int rgb = Integer.parseInt(colorHex, 16);
                color = Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }

            List<String> lore = s.getStringList("lore");

            // Parse tiers — new multi-tier format preferred.
            // Backward compat: if the old single 'ingredient' key is present, wrap it as tier level 1.
            List<AlchemyEffect.Tier> tiers = new ArrayList<>();
            ConfigurationSection tiersSection = s.getConfigurationSection("tiers");
            if (tiersSection != null) {
                for (String tierKey : tiersSection.getKeys(false)) {
                    ConfigurationSection ts = tiersSection.getConfigurationSection(tierKey);
                    if (ts == null) continue;
                    String ingStr = ts.getString("ingredient");
                    int level = ts.getInt("level", 1);
                    if (ingStr == null) continue;
                    tiers.add(new AlchemyEffect.Tier(resolveIngredientKey(ingStr), level));
                }
            } else {
                // List-of-maps format
                List<?> tierList = s.getList("tiers");
                if (tierList != null) {
                    for (Object entry : tierList) {
                        if (!(entry instanceof Map<?, ?> map)) continue;
                        String ingStr = (String) map.get("ingredient");
                        Object lvlObj = map.get("level");
                        int level = lvlObj instanceof Number n ? n.intValue() : 1;
                        if (ingStr == null) continue;
                        tiers.add(new AlchemyEffect.Tier(resolveIngredientKey(ingStr), level));
                    }
                }
            }

            // Old single-ingredient key (backward compat)
            if (tiers.isEmpty()) {
                String ingStr = s.getString("ingredient");
                if (ingStr != null) {
                    tiers.add(new AlchemyEffect.Tier(resolveIngredientKey(ingStr), 1));
                }
            }

            if (tiers.isEmpty()) {
                return LoadResult.failure("[" + path + "] " + id + " has no tiers or ingredient defined");
            }

            int maxLevel = s.getInt("max-level", 1);

            List<Integer> durations = new ArrayList<>();
            List<?> durList = s.getList("duration");
            if (durList != null) {
                for (Object o : durList) durations.add(((Number) o).intValue());
            }
            if (durations.isEmpty()) durations.add(60);

            StatRegistry statRegistry = ValmoraAPI.getInstance().getStatRegistry();
            Map<String, List<Double>> stats = new HashMap<>();
            ConfigurationSection statsSection = s.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String statKey : statsSection.getKeys(false)) {
                    String normalizedKey = statKey.toLowerCase();
                    if (!statRegistry.contains(normalizedKey)) continue;
                    List<?> vals = statsSection.getList(statKey);
                    List<Double> statValues = new ArrayList<>();
                    if (vals != null) {
                        for (Object o : vals) statValues.add(((Number) o).doubleValue());
                    }
                    stats.put(normalizedKey, statValues);
                }
            }

            return LoadResult.success(new AlchemyEffect(id, name, type, rarity, color, lore, tiers, maxLevel, durations, stats));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Failed to parse effect '" + id + "': " + e.getMessage());
        }
    }
}
