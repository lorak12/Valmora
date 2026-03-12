package org.nakii.valmora;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final PlayerManager playerManager;

    public PlayerConnectionListener(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // The event just tells the manager to do its job
        playerManager.handleJoin(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerManager.handleQuit(e.getPlayer().getUniqueId());
    }
}
