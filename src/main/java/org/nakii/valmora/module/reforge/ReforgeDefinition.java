package org.nakii.valmora.module.reforge;

import org.nakii.valmora.module.item.ItemType;

import java.util.List;
import java.util.Map;

public class ReforgeDefinition {

    private final String id;
    private final String name;
    private final List<ItemType> applicableTypes;
    private final Map<String, Double> statBonuses;
    private final double cost;

    public ReforgeDefinition(String id, String name, List<ItemType> applicableTypes,
                              Map<String, Double> statBonuses, double cost) {
        this.id = id;
        this.name = name;
        this.applicableTypes = applicableTypes;
        this.statBonuses = statBonuses;
        this.cost = cost;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<ItemType> getApplicableTypes() { return applicableTypes; }
    public Map<String, Double> getStatBonuses() { return statBonuses; }
    public double getCost() { return cost; }

    public boolean appliesTo(ItemType type) {
        if (applicableTypes.isEmpty()) return true;
        return applicableTypes.contains(ItemType.ALL) || applicableTypes.contains(type);
    }
}
