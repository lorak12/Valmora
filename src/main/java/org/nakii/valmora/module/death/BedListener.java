package org.nakii.valmora.module.death;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.zone.ZoneManager;
import org.nakii.valmora.util.Formatter;

/**
 * VANILLA_CONTROL_AUDIT.md §9 sleep row: gates bed entry via the new {@code ZoneFlags.sleeping}
 * flag (e.g. disable sleeping in a boss arena/dungeon). Bed-explodes-in-the-wrong-dimension is
 * handled by {@link RespawnAnchorListener} (both it and a respawn anchor fire the same
 * {@code BlockExplodeEvent} on this Paper version — see that class' doc).
 *
 * <p>{@code PlayerBedLeaveEvent}/{@code PlayerWakeUpEvent} are deliberately left alone — vanilla
 * already handles {@code Statistic.TIME_SINCE_REST} (phantom insomnia countdown) and, via the
 * already-shipped {@code world_rules} module, the {@code playersSleepingPercentage} GameRule.
 */
public class BedListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        ZoneManager zoneManager = ValmoraAPI.getInstance().getZoneManager();
        if (zoneManager == null) return;
        zoneManager.getZoneAt(player.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().sleeping()) {
                event.setCancelled(true);
                player.sendMessage(Formatter.format("<red>You can't sleep here."));
            }
        });
    }
}
