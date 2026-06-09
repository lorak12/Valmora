package org.nakii.valmora.module.quest.points;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread when a player's points in a category change. */
public class PointsChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String category;
    private final int newAmount;

    public PointsChangedEvent(Player player, String category, int newAmount) {
        this.player = player;
        this.category = category;
        this.newAmount = newAmount;
    }

    public Player getPlayer() { return player; }
    public String getCategory() { return category; }
    public int getNewAmount() { return newAmount; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
