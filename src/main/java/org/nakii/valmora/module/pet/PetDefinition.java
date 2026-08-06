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
    // Phase 3.3 (docs/REFACTOR/PROGRESS.md): pre-computed level->XP-needed table. Replaces the old
    // hardcoded `100 * level^2` static formula — each pet can now define its own xp-formula (or
    // inherit the server-wide default from pets/defaults.yml), resolved once at load time here.
    private final long[] xpThresholds;

    public PetDefinition(String id, String name, EntityType entityType,
                          Map<String, Double> baseStats, Map<String, Double> statsPerLevel,
                          List<PetAbilityDefinition> abilities,
                          TreeMap<Integer, List<String>> milestones,
                          long[] xpThresholds) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
        this.baseStats = baseStats;
        this.statsPerLevel = statsPerLevel;
        this.abilities = abilities;
        this.milestones = milestones;
        this.xpThresholds = xpThresholds;
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

    /** @return the max level reachable per this pet's precomputed XP table. */
    public int getMaxLevel() {
        return xpThresholds.length;
    }

    /** @return total XP required to reach {@code level} — looked up from the precomputed table, never re-evaluated. */
    public long xpForLevel(int level) {
        if (level <= 0) return 0L;
        if (level > xpThresholds.length) return xpThresholds[xpThresholds.length - 1];
        return xpThresholds[level - 1];
    }
}
