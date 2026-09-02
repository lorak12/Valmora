package org.nakii.valmora.module.skill;

public enum Skill {
    COMBAT("Combat", "Slay mobs to earn XP and level up."),
    FARMING("Farming", "Grow crops and trees to earn XP and level up."),
    FISHING("Fishing", "Catch fish and sea creatures to earn XP and level up."),
    MINING("Mining", "Mine ores and gems to earn XP and level up."),
    FORAGING("Foraging", "Forage herbs and mushrooms to earn XP and level up."),
    CARPENTRY("Carpentry", "Craft items to earn XP and level up."),
    ALCHEMY("Alchemy", "Brew potions to earn XP and level up."),
    ENCHANTING("Enchanting", "Enchant items to earn XP and level up."),
    TAMING("Taming", "Tame and raise pets to earn XP and level up.");

    private final String name;
    private final String description;

    Skill(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    // NOTE (HC-070): this legacy enum used to also carry a hardcoded maxLevel=60 per skill, which
    // silently duplicated (and could drift from) the data-driven SkillDefinition#getMaxLevel()
    // that SkillManager/SkillCommand actually consult (from skills/*.yml `max-level:`). It had no
    // remaining call sites, so it was removed rather than kept as a second, unused source of truth.
    // The XP-per-level curve lives in SkillRegistry (DEFAULT_XP_THRESHOLDS), which is the single
    // source of truth used by SkillManager. A duplicate copy and getLevelFromXp(double) that
    // previously lived here were unused and removed.
}
