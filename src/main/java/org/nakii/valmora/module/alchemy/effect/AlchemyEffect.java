package org.nakii.valmora.module.alchemy.effect;

import org.bukkit.Color;
import org.bukkit.Material;
import org.nakii.valmora.module.stat.Stat;

import java.util.List;
import java.util.Map;

public class AlchemyEffect {

    private final String id;
    private final String name;
    private final AlchemyEffectType type;
    private final String rarity;
    private final Color color;
    private final List<String> lore;
    private final Material ingredient;
    private final int maxLevel;
    private final List<Integer> durations;
    private final Map<Stat, List<Double>> stats;

    public AlchemyEffect(String id, String name, AlchemyEffectType type, String rarity,
                         Color color, List<String> lore, Material ingredient,
                         int maxLevel, List<Integer> durations, Map<Stat, List<Double>> stats) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.rarity = rarity;
        this.color = color;
        this.lore = lore;
        this.ingredient = ingredient;
        this.maxLevel = maxLevel;
        this.durations = durations;
        this.stats = stats;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public AlchemyEffectType getType() { return type; }
    public String getRarity() { return rarity; }
    public Color getColor() { return color; }
    public List<String> getLore() { return lore; }
    public Material getIngredient() { return ingredient; }
    public int getMaxLevel() { return maxLevel; }

    public int getDuration(int level) {
        int idx = Math.min(level - 1, durations.size() - 1);
        return durations.get(idx);
    }

    public Map<Stat, List<Double>> getStats() { return stats; }

    public double getStatValue(Stat stat, int level) {
        List<Double> values = stats.get(stat);
        if (values == null || values.isEmpty()) return 0;
        int idx = Math.min(level - 1, values.size() - 1);
        return values.get(idx);
    }
}
