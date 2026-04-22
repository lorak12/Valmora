package org.nakii.valmora.module.skill;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkillXpGainEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    
    private final Player player;
    private final SkillDefinition skill;
    private final double xp;
    
    public SkillXpGainEvent(Player player, SkillDefinition skill, double xp) {
        this.player = player;
        this.skill = skill;
        this.xp = xp;
    }

    public Player getPlayer() {
        return player;
    }

    public SkillDefinition getSkill() {
        return skill;
    }

    public double getXp() {
        return xp;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
