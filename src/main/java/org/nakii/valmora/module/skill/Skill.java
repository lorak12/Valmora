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

    public static int[] xpTresholds = {
    10,
    20,
    50,
    100,
    200,
    500,
    1000,
    1500,
    2000,
    3000,
    5000,
    7500,
    10000,
    15000,
    20000,
    30000,
    40000,
    50000,
    60000,
    75000,
    100000,
    125000,
    150000,
    175000,
    200000,
    250000,
    300000,
    350000,
    400000,
    450000,
    500000,
    600000,
    700000,
    800000,
    900000,
    1000000,
    1200000,
    1400000,
    1600000,
    1800000,
    2000000,
    2300000,
    2600000,
    3000000,
    3400000,
    3800000,
    4200000,
    4600000,
    5000000,
    5500000,
    6000000,
    6500000,
    7000000,
    7500000,
    8000000,
    8500000,
    9000000,
    9500000,
    10000000
};

    public int getLevelFromXp(double totalXp){
        for (int i = 0; i < xpTresholds.length; i++) {
            if (totalXp < xpTresholds[i]) {
                return i;
            }
        }
        return maxLevel;
    }
}
