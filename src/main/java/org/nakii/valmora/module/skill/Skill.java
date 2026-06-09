package org.nakii.valmora.module.skill;

public enum Skill {
    COMBAT("Combat", "Slay mobs to earn XP and level up.", 60),
    FARMING("Farming", "Grow crops and trees to earn XP and level up.", 60),
    FISHING("Fishing", "Catch fish and sea creatures to earn XP and level up.", 60),
    MINING("Mining", "Mine ores and gems to earn XP and level up.", 60),
    FORAGING("Foraging", "Forage herbs and mushrooms to earn XP and level up.", 60),
    CRAFTING("Crafting", "Craft items to earn XP and level up.", 60),
    ALCHEMY("Alchemy", "Brew potions to earn XP and level up.", 60),
    ENCHANTING("Enchanting", "Enchant items to earn XP and level up.", 60);  

    private final String name;
    private final String description;
    private final int maxLevel;

    Skill(String name, String description, int maxLevel) {
        this.name = name;
        this.description = description;
        this.maxLevel = maxLevel;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    // NOTE: The XP-per-level curve lives in SkillRegistry (DEFAULT_XP_THRESHOLDS),
    // which is the single source of truth used by SkillManager. A duplicate copy
    // and getLevelFromXp(double) that previously lived here were unused and removed.
}
