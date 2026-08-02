package org.nakii.valmora.module.progression.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProgressionTreeResetEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String treeId;

    public ProgressionTreeResetEvent(Player player, String treeId) {
        this.player = player;
        this.treeId = treeId;
    }

    public Player getPlayer() { return player; }
    public String getTreeId() { return treeId; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
