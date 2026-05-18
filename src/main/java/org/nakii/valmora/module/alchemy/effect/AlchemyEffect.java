package org.nakii.valmora.module.alchemy.effect;

import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class AlchemyEffect {

    /**
     * A single base-recipe tier.
     * ingredientKey is either "minecraft:<material>" for vanilla items
     * or the custom Valmora item ID for non-vanilla ingredients.
     */
    public record Tier(String ingredientKey, int level) {}

    private final String id;
    private final String name;
    private final AlchemyEffectType type;
    private final String rarity;
    private final Color color;
    private final List<String> lore;
    private final List<Tier> tiers;
    private final int maxLevel;
    private final List<Integer> durations;
    private final Map<String, List<Double>> stats;

    public AlchemyEffect(String id, String name, AlchemyEffectType type, String rarity,
                         Color color, List<String> lore, List<Tier> tiers,
                         int maxLevel, List<Integer> durations, Map<String, List<Double>> stats) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.rarity = rarity;
        this.color = color;
        this.lore = lore;
        this.tiers = tiers;
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
    public List<Tier> getTiers() { return tiers; }
    public int getMaxLevel() { return maxLevel; }

    /** The highest level achievable through base recipes alone (no level modifiers). */
    public int getMaxBaseLevel() {
        return tiers.stream().mapToInt(Tier::level).max().orElse(1);
    }

    public java.util.Optional<Tier> getTierForIngredient(String ingredientKey) {
        return tiers.stream().filter(t -> t.ingredientKey().equalsIgnoreCase(ingredientKey)).findFirst();
    }

    public int getDuration(int level) {
        int idx = Math.min(level - 1, durations.size() - 1);
        return durations.get(idx);
    }

    public Map<String, List<Double>> getStats() { return stats; }

    public double getStatValue(String statId, int level) {
        List<Double> values = stats.get(statId.toLowerCase());
        if (values == null || values.isEmpty()) return 0;
        int idx = Math.min(level - 1, values.size() - 1);
        return values.get(idx);
    }
}
