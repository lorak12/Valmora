package org.nakii.valmora.module.skill;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillDefinitionParser {

    public static LoadResult<SkillDefinition, String> parse(String id, ConfigurationSection section, String filePath, Valmora plugin) {
        try {
            String name = section.getString("name", id);
            String description = String.join("\n", section.getStringList("description"));
            Material material = Material.matchMaterial(section.getString("material", "BOOK"));
            int maxLevel = section.getInt("max-level", 60);
            String xpCurve = section.getString("xp-curve", "default");

            // Parse Sources
            Map<String, Map<String, Double>> sources = new HashMap<>();
            ConfigurationSection sourcesSec = section.getConfigurationSection("sources");
            if (sourcesSec != null) {
                for (String type : sourcesSec.getKeys(false)) {
                    ConfigurationSection typeSec = sourcesSec.getConfigurationSection(type);
                    Map<String, Double> identifiers = new HashMap<>();
                    if (typeSec != null) {
                        for (String key : typeSec.getKeys(false)) {
                            identifiers.put(key, typeSec.getDouble(key));
                        }
                    }
                    sources.put(type.toUpperCase(), identifiers);
                }
            }

            // Parse Rewards
            CompiledEvent perLevelReward = null;
            if (section.contains("rewards.per-level")) {
                perLevelReward = plugin.getScriptModule().getEventParser().parseList(section.getStringList("rewards.per-level"));
            }

            Map<Integer, CompiledEvent> milestoneRewards = new HashMap<>();
            ConfigurationSection milestonesSec = section.getConfigurationSection("rewards.milestones");
            if (milestonesSec != null) {
                for (String levelKey : milestonesSec.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelKey);
                        CompiledEvent event = plugin.getScriptModule().getEventParser().parseList(milestonesSec.getStringList(levelKey));
                        milestoneRewards.put(level, event);
                    } catch (NumberFormatException ignored) {}
                }
            }

            return LoadResult.success(new SkillDefinition(id, name, description, material, maxLevel, xpCurve, sources, perLevelReward, milestoneRewards));

        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse skill: " + e.getMessage());
        }
    }
}