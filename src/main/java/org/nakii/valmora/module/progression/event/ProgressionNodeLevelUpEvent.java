package org.nakii.valmora.module.progression.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProgressionNodeLevelUpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String treeId;
    private final String nodeId;
    private final int newLevel;

    public ProgressionNodeLevelUpEvent(Player player, String treeId, String nodeId, int newLevel) {
        this.player = player;
        this.treeId = treeId;
        this.nodeId = nodeId;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public String getTreeId() { return treeId; }
    public String getNodeId() { return nodeId; }
    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
