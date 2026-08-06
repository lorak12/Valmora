package org.nakii.valmora.module.notify;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.nakii.valmora.module.notify.io.BossBarIO;

/** Cleans up per-player notify IO state (currently: pending boss-bar auto-hide tasks) on quit. */
public class NotifyQuitListener implements Listener {

    private final BossBarIO bossBarIO;

    public NotifyQuitListener(BossBarIO bossBarIO) {
        this.bossBarIO = bossBarIO;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossBarIO.clearFor(event.getPlayer().getUniqueId());
    }
}
