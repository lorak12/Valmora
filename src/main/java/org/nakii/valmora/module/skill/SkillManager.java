package org.nakii.valmora.module.skill;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class SkillManager {
    
    // Maps Skill ID (String) to XP amount
    private final Map<String, Double> skillXp = new HashMap<>();

    public SkillManager() {
    }

    public void loadData(Map<String, Double> savedData) {
        this.skillXp.clear();
        if (savedData != null) {
            for (Map.Entry<String, Double> entry : savedData.entrySet()) {
                this.skillXp.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }
    }

    public Map<String, Double> getSaveData() {
        return new HashMap<>(skillXp);
    }

    public SkillRegistry getSkillRegistry() {
        return Valmora.getInstance().getSkillModule().getSkillRegistry();
    }

    public double getXp(String skillId) {
        return skillXp.getOrDefault(skillId.toLowerCase(), 0.0);
    }

    public double getXp(Skill skill) {
        return getXp(skill.name());
    }

    public int getLevel(String skillId) {
        SkillRegistry registry = getSkillRegistry();
        SkillDefinition skill = registry.getSkill(skillId).orElse(null);
        
        if (skill == null) return 0;
        
        return registry.getLevelFromXp(skill.getXpCurve(), getXp(skillId));
    }

    public int getLevel(Skill skill) {
        return getLevel(skill.name());
    }

    public int getLevelFromXp(String curveId, double xp) {
        return getSkillRegistry().getLevelFromXp(curveId, xp);
    }

    public void setXp(String skillId, double amount) {
        skillXp.put(skillId.toLowerCase(), amount);
    }

    public void setXp(Skill skill, double amount) {
        setXp(skill.name(), amount);
    }

    public void addXp(String skillId, double amount, Player player) {
        SkillRegistry registry = getSkillRegistry();
        SkillDefinition skill = registry.getSkill(skillId).orElse(null);
        
        if (skill == null) return;

        double currentXp = getXp(skillId);
        int oldLevel = registry.getLevelFromXp(skill.getXpCurve(), currentXp);

        // Stop adding XP if they are already at the max level
        if (oldLevel >= skill.getMaxLevel()) {
            return;
        }

        double newXp = currentXp + amount;
        skillXp.put(skillId.toLowerCase(), newXp);

        // Call XP Gain Event
        SkillXpGainEvent event = new SkillXpGainEvent(player, skill, newXp);
        event.callEvent();

        int newLevel = registry.getLevelFromXp(skill.getXpCurve(), newXp);
        
        // Cap to the skill's max level
        if (newLevel > skill.getMaxLevel()) {
            newLevel = skill.getMaxLevel();
        }

        if (newLevel > oldLevel) {
            // Call Level Up Event
            SkillLevelUpEvent levelUpEvent = new SkillLevelUpEvent(player, skill, oldLevel, newLevel);
            levelUpEvent.callEvent();

            // Prepare ExecutionContext for Script Engine rewards
            MemoryConfiguration params = new MemoryConfiguration();
            ExecutionContext context = new SimpleExecutionContext(player, player, player.getLocation(), params);

            // Execute rewards for EVERY level gained (in case they gained multiple levels at once)
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                // Update the dynamic $param.level$ variable
                params.set("level", lvl); 

                // Execute Per-Level Reward (if defined)
                if (skill.getPerLevelReward() != null) {
                    skill.getPerLevelReward().execute(context);
                }

                // Execute Milestone Reward (if defined for this specific level)
                if (skill.getMilestoneRewards() != null && skill.getMilestoneRewards().containsKey(lvl)) {
                    skill.getMilestoneRewards().get(lvl).execute(context);
                }
            }
        }
    }

    public void addXp(Skill skill, double amount, Player player) {
        addXp(skill.name(), amount, player);
    }
}