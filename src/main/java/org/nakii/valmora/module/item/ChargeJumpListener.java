package org.nakii.valmora.module.item;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;
import org.nakii.valmora.module.item.impl.ChargeJumpTracker;

/**
 * Handles the release side of {@link org.nakii.valmora.module.item.impl.ChargeJumpMechanic}
 * (Spring Boots' "To the Moon!" — see docs/IMPLEMENTATION_BACKLOG.md's item-mechanic-engine
 * CHARGE_JUMP item). The charge-<i>start</i> side already fires through the normal SNEAK
 * ability-trigger pipeline ({@code AbilityTriggerListener.onSneak}); this listener only handles
 * un-sneaking, launching the player if (and only if) {@link ChargeJumpTracker} has a charge
 * recorded for them.
 */
public class ChargeJumpListener implements Listener {

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) return; // charge-start is handled by the SNEAK ability trigger
        Player player = event.getPlayer();
        ChargeJumpTracker.releaseCharge(player.getUniqueId()).ifPresent(force -> {
            Vector velocity = player.getVelocity();
            velocity.setY(force);
            player.setVelocity(velocity);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Avoid leaking a stale charge entry for a player who logs out mid-charge.
        ChargeJumpTracker.cancelCharge(event.getPlayer().getUniqueId());
    }
}
