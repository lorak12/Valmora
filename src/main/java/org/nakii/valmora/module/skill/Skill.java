package org.nakii.valmora.module.skill;

public enum Skill {
    COMBAT("Combat", "Slay mobs to earn XP and level up.", 60),
    FARMING("Farming", "Grow crops and trees to earn XP and level up.", 60),
    FISHING("Fishing", "Catch fish and sea creatures to earn XP and level up.", 60),
    MINING("Mining", "Mine ores and gems to earn XP and level up.", 60),
    FORAGING("Foraging", "Forage herbs and mushrooms to earn XP and level up.", 60);

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
        50,
        100,
        250,
        500,
        1000,
        1500,
        2000,
        5000,
        10000,
        15000,
        20000,
        25000,
        30000,
        35000,
        40000,
        45000,
        50000,
        55000,
        60000,
        65000,
        70000,
        75000,
        80000,
        85000,
        90000,
        95000,
        100000,
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
