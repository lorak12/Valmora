package org.nakii.valmora.module.fishing;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishingListener implements Listener {

    private final FishingManager fishingManager;

    public FishingListener(FishingManager fishingManager) {
        this.fishingManager = fishingManager;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.BITE) {
            if (event.getHook() != null) fishingManager.playBiteFeedback(event.getHook().getLocation());
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        // Bobber location before removing the caught entity — used as the sea-creature spawn point.
        var hookLocation = event.getHook() != null ? event.getHook().getLocation() : event.getPlayer().getLocation();
        if (event.getCaught() != null) event.getCaught().remove();
        event.setCancelled(true);
        fishingManager.handleCatch(event.getPlayer(), hookLocation);
    }
}
