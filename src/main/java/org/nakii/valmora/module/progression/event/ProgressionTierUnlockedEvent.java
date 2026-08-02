package org.nakii.valmora.module.progression.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProgressionTierUnlockedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String treeId;
    private final int tierIndex;

    public ProgressionTierUnlockedEvent(Player player, String treeId, int tierIndex) {
        this.player = player;
        this.treeId = treeId;
        this.tierIndex = tierIndex;
    }

    public Player getPlayer() { return player; }
    public String getTreeId() { return treeId; }
    public int getTierIndex() { return tierIndex; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
