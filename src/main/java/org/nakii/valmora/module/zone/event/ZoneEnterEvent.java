package org.nakii.valmora.module.zone.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.module.zone.ZoneDefinition;

public class ZoneEnterEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ZoneDefinition zone;

    public ZoneEnterEvent(Player player, ZoneDefinition zone) {
        this.player = player;
        this.zone = zone;
    }

    public Player getPlayer() { return player; }
    public ZoneDefinition getZone() { return zone; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
