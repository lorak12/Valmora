package org.nakii.valmora.module.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EconomyListener implements Listener {

    private final EconomyModule module;

    public EconomyListener(EconomyModule module) {
        this.module = module;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        var uuid = event.getEntity().getUniqueId();
        double purse = module.getPurse(uuid);
        if (purse > 0) {
            module.removePurse(uuid, purse / 2.0);
        }
    }
}
