package org.nakii.valmora.module.progression;

import org.bukkit.Material;

import java.util.List;

public class ProgressionNode {
    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final int tierIndex;
    private final int maxLevel;
    private final String costCurve;
    private final List<String> prerequisiteNodeIds;
    private final StatBonus statBonus;
    private final DailyBonus dailyBonus;

    public ProgressionNode(String id, String displayName, String description, Material icon,
                           int tierIndex, int maxLevel, String costCurve,
                           List<String> prerequisiteNodeIds, StatBonus statBonus, DailyBonus dailyBonus) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.tierIndex = tierIndex;
        this.maxLevel = maxLevel;
        this.costCurve = costCurve;
        this.prerequisiteNodeIds = prerequisiteNodeIds != null ? prerequisiteNodeIds : List.of();
        this.statBonus = statBonus;
        this.dailyBonus = dailyBonus;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
    public int getTierIndex() { return tierIndex; }
    public int getMaxLevel() { return maxLevel; }
    public String getCostCurve() { return costCurve; }
    public List<String> getPrerequisiteNodeIds() { return prerequisiteNodeIds; }
    public StatBonus getStatBonus() { return statBonus; }
    public DailyBonus getDailyBonus() { return dailyBonus; }

    public record StatBonus(String stat, double perLevel) {}
    public record DailyBonus(String category, double perLevel) {}
}
