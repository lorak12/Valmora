package org.nakii.valmora.module.reforge;

import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.item.Rarity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ReforgeDefinition {

    private final String id;
    private final String name;
    private final List<ItemType> applicableTypes;
    private final Map<Rarity, Map<String, Double>> statBonusesByRarity;
    private final boolean generateStone;
    /** Relative weight for Random Forge selection — higher rolls more often. Default 1.0 (uniform). */
    private final double weight;

    public ReforgeDefinition(String id, String name, List<ItemType> applicableTypes,
                              Map<Rarity, Map<String, Double>> statBonusesByRarity,
                              boolean generateStone) {
        this(id, name, applicableTypes, statBonusesByRarity, generateStone, 1.0);
    }

    public ReforgeDefinition(String id, String name, List<ItemType> applicableTypes,
                              Map<Rarity, Map<String, Double>> statBonusesByRarity,
                              boolean generateStone, double weight) {
        this.id = id;
        this.name = name;
        this.applicableTypes = applicableTypes;
        this.statBonusesByRarity = new EnumMap<>(statBonusesByRarity);
        this.generateStone = generateStone;
        this.weight = weight > 0 ? weight : 1.0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<ItemType> getApplicableTypes() { return applicableTypes; }
    public Map<Rarity, Map<String, Double>> getStatBonusesByRarity() { return statBonusesByRarity; }
    public boolean isGenerateStone() { return generateStone; }
    public double getWeight() { return weight; }

    /** Returns the stat bonuses for the given rarity, falling back to the nearest lower rarity. */
    public Map<String, Double> getStatBonusesForRarity(Rarity rarity) {
        Map<String, Double> exact = statBonusesByRarity.get(rarity);
        if (exact != null) return exact;
        Rarity[] values = Rarity.values();
        for (int i = rarity.ordinal() - 1; i >= 0; i--) {
            Map<String, Double> fallback = statBonusesByRarity.get(values[i]);
            if (fallback != null) return fallback;
        }
        return Map.of();
    }

    public boolean appliesTo(ItemType type) {
        if (applicableTypes.isEmpty()) return true;
        return applicableTypes.contains(ItemType.ALL) || applicableTypes.contains(type);
    }
}
