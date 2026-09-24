package org.nakii.valmora.module.death;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.zone.ZoneManager;

/**
 * VANILLA_CONTROL_AUDIT.md §9 phantom-insomnia row. Cancels phantom spawns when either
 * {@code death.phantoms-enabled} is {@code false} (global toggle, default {@code true} = vanilla)
 * or the target player's current zone has {@code naturalMobSpawning == false} — reusing the
 * existing flag ({@code ZoneListener} already applies it to {@code CreatureSpawnEvent}) rather than
 * adding a redundant one, since a phantom spawn is itself just a natural spawn from the player's
 * perspective. Doesn't touch {@code Statistic.TIME_SINCE_REST} — vanilla's own countdown/reset
 * behavior on sleep is left untouched.
 */
public class PhantomInsomniaListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPhantomPreSpawn(PhantomPreSpawnEvent event) {
        if (!(event.getSpawningEntity() instanceof Player player)) return;

        Valmora plugin = Valmora.getInstance();
        if (!plugin.getConfig().getBoolean("death.phantoms-enabled", true)) {
            event.setCancelled(true);
            return;
        }

        ZoneManager zoneManager = ValmoraAPI.getInstance().getZoneManager();
        if (zoneManager == null) return;
        zoneManager.getZoneAt(player.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().naturalMobSpawning()) {
                event.setCancelled(true);
            }
        });
    }
}
