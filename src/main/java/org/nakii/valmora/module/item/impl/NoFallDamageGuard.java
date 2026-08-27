package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cancels the next fall-damage hit for a player who was just launched by an ability configured
 * with {@code no-fall-damage: true} (e.g. {@code LAUNCH_PLAYER}). One-shot: the flag is consumed
 * by the first FALL-cause {@link EntityDamageEvent} that lands after {@link #protect}, so a later,
 * unrelated fall still damages the player normally.
 */
public class NoFallDamageGuard implements Listener {

    // Static so LaunchPlayerMechanic (a stateless, no-arg-constructed AbilityMechanic — see the
    // registration pattern in AbilityManager) can flag a player without holding a reference to
    // this listener instance, matching ChargeJumpTracker's static-state convention used for the
    // sibling CHARGE_JUMP mechanic.
    private static final Set<UUID> protectedPlayers = ConcurrentHashMap.newKeySet();

    /** Marks {@code uuid} to have their next fall-damage instance cancelled. */
    public static void protect(UUID uuid) {
        protectedPlayers.add(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (protectedPlayers.remove(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFallDistance(0f);
        }
    }
}
