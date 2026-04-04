package org.nakii.valmora.module.skill;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.entity.Player;

public class SkillManager {
    
    private final Map<Skill, Double> skillXp = new EnumMap<>(Skill.class);

    public SkillManager() {
        for (Skill skill : Skill.values()) {
            skillXp.put(skill, 0.0);
        }
    }

    public void loadData(Map<Skill, Double> savedData) {
        for (Map.Entry<Skill, Double> entry : savedData.entrySet()) {
            skillXp.put(entry.getKey(), entry.getValue());
        }
    }

    public Map<Skill, Double> getSaveData() {
        return new EnumMap<>(skillXp);
    }

    public double getXp(Skill skill){
        return skillXp.get(skill);
    }

    public int getLevel(Skill skill){
        return skill.getLevelFromXp(getXp(skill));
    }

    public void setXp(Skill skill, double amount){
        skillXp.put(skill, amount);
    }

    public void addXp(Skill skill, double amount, Player player){
        double currentXp = getXp(skill);
        int oldLevel = getLevel(skill);

        double newXp = currentXp + amount;
        skillXp.put(skill, newXp);

        SkillXpGainEvent event = new SkillXpGainEvent(player, skill, newXp);
        event.callEvent();

        int newLevel = skill.getLevelFromXp(newXp);
        if (newLevel > oldLevel){
            // call an event for level up
            SkillLevelUpEvent levelUpEvent = new SkillLevelUpEvent(player, skill, oldLevel, newLevel);
            levelUpEvent.callEvent();
        }
    }

    
}
