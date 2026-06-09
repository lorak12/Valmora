package org.nakii.valmora.module.profile.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread when a tag is added to a player's active profile. */
public class TagAddedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String tag;

    public TagAddedEvent(Player player, String tag) {
        this.player = player;
        this.tag = tag;
    }

    public Player getPlayer() { return player; }
    public String getTag() { return tag; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
