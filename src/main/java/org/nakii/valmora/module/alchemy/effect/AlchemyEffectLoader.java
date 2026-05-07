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

            String ingStr = s.getString("ingredient");
            if (ingStr == null) return LoadResult.failure("[" + path + "] " + id + " missing 'ingredient'");
            Material ingredient = Material.matchMaterial(ingStr.toUpperCase());
            if (ingredient == null) return LoadResult.failure("[" + path + "] " + id + " unknown ingredient: " + ingStr);

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
                    if (!statRegistry.contains(normalizedKey)) continue; // silently skip unknown stats
                    List<?> vals = statsSection.getList(statKey);
                    List<Double> statValues = new ArrayList<>();
                    if (vals != null) {
                        for (Object o : vals) statValues.add(((Number) o).doubleValue());
                    }
                    stats.put(normalizedKey, statValues);
                }
            }

            return LoadResult.success(new AlchemyEffect(id, name, type, rarity, color, lore, ingredient, maxLevel, durations, stats));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Failed to parse effect '" + id + "': " + e.getMessage());
        }
    }
}
