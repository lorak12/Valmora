package org.nakii.valmora.module.npc.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.module.npc.NpcDefinition;

public class NpcInteractEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final NpcDefinition npc;

    public NpcInteractEvent(Player player, NpcDefinition npc) {
        this.player = player;
        this.npc = npc;
    }

    public Player getPlayer() { return player; }
    public NpcDefinition getNpc() { return npc; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
