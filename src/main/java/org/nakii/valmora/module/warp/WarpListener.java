package org.nakii.valmora.module.warp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class WarpListener implements Listener {

    private final WarpManager warpManager;

    public WarpListener(WarpManager warpManager) {
        this.warpManager = warpManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        var loc = event.getTo();
        warpManager.getWarpByPad(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                .ifPresent(warp -> warpManager.teleport(event.getPlayer(), warp));
    }
}
