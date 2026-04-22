package org.nakii.valmora.module.skill;

import org.nakii.valmora.api.registry.SimpleRegistry;
import java.util.Optional;

public class SkillRegistry extends SimpleRegistry<SkillDefinition> {

    // The universal curve you previously had in the Enum
    private static final int[] DEFAULT_XP_THRESHOLDS = {
        10, 20, 50, 100, 200, 500, 1000, 1500, 2000, 3000, 5000, 7500, 10000, 
        15000, 20000, 30000, 40000, 50000, 60000, 75000, 100000, 125000, 150000, 
        175000, 200000, 250000, 300000, 350000, 400000, 450000, 500000, 600000, 
        700000, 800000, 900000, 1000000, 1200000, 1400000, 1600000, 1800000, 
        2000000, 2300000, 2600000, 3000000, 3400000, 3800000, 4200000, 4600000, 
        5000000, 5500000, 6000000, 6500000, 7000000, 7500000, 8000000, 8500000, 
        9000000, 9500000, 10000000
    };

    public void registerSkill(SkillDefinition definition) {
        register(definition.getId(), definition);
    }

    public Optional<SkillDefinition> getSkill(String id) {
        return get(id);
    }

    public int getLevelFromXp(String curveId, double xp) {
        int[] thresholds = DEFAULT_XP_THRESHOLDS;
        
        for (int i = 0; i < thresholds.length; i++) {
            if (xp < thresholds[i]) {
                return i;
            }
        }
        return thresholds.length; 
    }

    public double getXpForLevel(String curveId, int level) {
        int[] thresholds = DEFAULT_XP_THRESHOLDS;
        if (level <= 0) return 0;
        if (level > thresholds.length) return thresholds[thresholds.length - 1];
        return thresholds[level - 1];
    }

    public record ProgressData(int currentLevel, int nextLevel, int xpInLevel, int xpRequired, int percent) {}

    public ProgressData getProgressData(String curveId, double totalXp) {
        int level = getLevelFromXp(curveId, totalXp);
        int maxLvl = DEFAULT_XP_THRESHOLDS.length;
        
        double currentLvlXp = getXpForLevel(curveId, level);
        double nextLvlXp = getXpForLevel(curveId, level + 1);
        
        int xpInLevel = (int) (totalXp - currentLvlXp);
        int xpRequired = (int) (nextLvlXp - currentLvlXp);
        int percent = xpRequired > 0 ? (int) ((double) xpInLevel / xpRequired * 100) : 100;
        
        return new ProgressData(level, Math.min(level + 1, maxLvl), xpInLevel, xpRequired, percent);
    }
}