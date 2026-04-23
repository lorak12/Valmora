package org.nakii.valmora.module.enchant;

import org.nakii.valmora.module.item.ItemType;

import java.util.List;

public class EnchantmentDefinition {

    private final String id;
    private final String name;
    private final List<String> description;
    private final int etableMaxLevel;
    private final int absoluteMaxLevel;
    private final List<ItemType> targets;
    private final List<String> conflicts;
    private final EnchantmentLogic logic;

    public EnchantmentDefinition(String id, String name, List<String> description, int etableMaxLevel,
                              int absoluteMaxLevel, List<ItemType> targets, List<String> conflicts,
                              EnchantmentLogic logic) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.etableMaxLevel = etableMaxLevel;
        this.absoluteMaxLevel = absoluteMaxLevel;
        this.targets = targets;
        this.conflicts = conflicts;
        this.logic = logic;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public int getEtableMaxLevel() {
        return etableMaxLevel;
    }

    public int getAbsoluteMaxLevel() {
        return absoluteMaxLevel;
    }

    public List<ItemType> getTargets() {
        return targets;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public EnchantmentLogic getLogic() {
        return logic;
    }

    public boolean canApplyTo(ItemType type) {
        return targets.contains(type);
    }

    public boolean conflictsWith(String otherId) {
        return conflicts.contains(otherId.toLowerCase());
    }
}