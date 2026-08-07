package org.nakii.valmora.module.warp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.nakii.valmora.Valmora;

public class WarpListener implements Listener {

    private final Valmora plugin;
    private final WarpManager warpManager;

    public WarpListener(Valmora plugin, WarpManager warpManager) {
        this.plugin = plugin;
        this.warpManager = warpManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        checkPad(event.getPlayer(), event.getTo());
    }

    /**
     * Triggers a warp pad for a player who arrives on top of one without ever generating a
     * qualifying {@link PlayerMoveEvent} block-change — joining already standing on a pad, or
     * being teleported/portaled directly onto one (added 2026-08-07; previously only movement
     * onto a pad was ever detected).
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        checkPad(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Deferred a tick so the player's location has settled post-teleport before checking pad
        // membership at the destination.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) checkPad(event.getPlayer(), event.getPlayer().getLocation());
        });
    }

    private void checkPad(Player player, Location loc) {
        if (loc.getWorld() == null) return;
        warpManager.getWarpByPad(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                .ifPresent(warp -> warpManager.teleport(player, warp));
    }
}
