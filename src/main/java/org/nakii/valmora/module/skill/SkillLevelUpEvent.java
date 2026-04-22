package org.nakii.valmora.module.skill;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkillLevelUpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final Player player;
    private final SkillDefinition skill;
    private final int oldLevel;
    private final int newLevel;
    
    public SkillLevelUpEvent(Player player, SkillDefinition skill, int oldLevel, int newLevel) {
        this.player = player;
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public SkillDefinition getSkill() {
        return skill;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
