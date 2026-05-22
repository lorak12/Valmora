package org.nakii.valmora.module.pet;

import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PetDefinition {

    private final String id;
    private final String name;
    private final EntityType entityType;
    private final Map<String, Double> baseStats;
    private final Map<String, Double> statsPerLevel;
    private final List<PetAbilityDefinition> abilities;
    private final TreeMap<Integer, List<String>> milestones; // level → DSL event list (raw strings)

    public PetDefinition(String id, String name, EntityType entityType,
                          Map<String, Double> baseStats, Map<String, Double> statsPerLevel,
                          List<PetAbilityDefinition> abilities,
                          TreeMap<Integer, List<String>> milestones) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
        this.baseStats = baseStats;
        this.statsPerLevel = statsPerLevel;
        this.abilities = abilities;
        this.milestones = milestones;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public EntityType getEntityType() { return entityType; }
    public Map<String, Double> getBaseStats() { return baseStats; }
    public Map<String, Double> getStatsPerLevel() { return statsPerLevel; }
    public List<PetAbilityDefinition> getAbilities() { return abilities; }
    public TreeMap<Integer, List<String>> getMilestones() { return milestones; }

    public Map<String, Double> computeStats(int level) {
        Map<String, Double> result = new java.util.HashMap<>(baseStats);
        for (Map.Entry<String, Double> entry : statsPerLevel.entrySet()) {
            result.merge(entry.getKey(), entry.getValue() * level, Double::sum);
        }
        return result;
    }

    public static long xpForLevel(int level) {
        return 100L * level * level;
    }
}
