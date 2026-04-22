package org.nakii.valmora.module.skill;

import org.bukkit.Material;
import org.nakii.valmora.api.scripting.CompiledEvent;
import java.util.Map;

public class SkillDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final Material material;
    private final int maxLevel;
    private final String xpCurve;
    private final Map<String, Map<String, Double>> sources; // e.g. "BLOCK_BREAK" -> {"STONE": 5.0}
    private final CompiledEvent perLevelReward;
    private final Map<Integer, CompiledEvent> milestoneRewards;

    public SkillDefinition(String id, String name, String description, Material material, int maxLevel, String xpCurve,
                           Map<String, Map<String, Double>> sources, CompiledEvent perLevelReward, Map<Integer, CompiledEvent> milestoneRewards) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.material = material;
        this.maxLevel = maxLevel;
        this.xpCurve = xpCurve;
        this.sources = sources;
        this.perLevelReward = perLevelReward;
        this.milestoneRewards = milestoneRewards;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public int getMaxLevel() { return maxLevel; }
    public String getXpCurve() { return xpCurve; }
    public Map<String, Map<String, Double>> getSources() { return sources; }
    public CompiledEvent getPerLevelReward() { return perLevelReward; }
    public Map<Integer, CompiledEvent> getMilestoneRewards() { return milestoneRewards; }

    public Double getSourceXp(String sourceType, String identifier) {
        Map<String, Double> typeMap = sources.get(sourceType.toUpperCase());
        if (typeMap != null) {
            return typeMap.get(identifier.toUpperCase());
        }
        return null;
    }
}