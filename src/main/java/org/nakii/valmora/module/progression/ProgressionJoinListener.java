package org.nakii.valmora.module.progression;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.nakii.valmora.module.profile.PlayerProfileLoadedEvent;

/**
 * Grants a join-triggered catch-up check for the daily progression bonus, instead of relying
 * solely on the 5-minute poll in {@link ProgressionModule#processDailyBonuses()} — a player who
 * was offline past the 24h window previously had to wait up to 5 minutes after logging back in.
 */
public class ProgressionJoinListener implements Listener {

    private final ProgressionModule module;

    public ProgressionJoinListener(ProgressionModule module) {
        this.module = module;
    }

    @EventHandler
    public void onProfileLoaded(PlayerProfileLoadedEvent event) {
        var player = Bukkit.getPlayer(event.getUuid());
        if (player != null) {
            module.processDailyBonusFor(player);
        }
    }
}
