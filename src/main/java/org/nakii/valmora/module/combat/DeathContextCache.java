package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the most recent damage context (type/attacker/weapon) applied to each player, independent
 * of vanilla's own {@code Player#getLastDamageCause()}. This is necessary because player health is
 * fully virtualized ({@link DamageApplier}/{@code PlayerManager.syncVisualHealth}) and death is
 * force-triggered via a direct {@code player.setHealth(0)} call outside the normal vanilla
 * damage-event flow — vanilla's own "last damage cause" bookkeeping can't be trusted to reflect the
 * hit that actually killed the player (see {@code docs/modules/design/death.md}).
 *
 * <p>Updated on every damage application to a player (not just fatal ones — mirrors how vanilla's
 * own last-damage-cause behaves), consumed once by the {@code death} module's
 * {@code DeathMessageService} when a {@code PlayerDeathEvent} fires.
 */
public final class DeathContextCache {

    private DeathContextCache() {}

    public record Context(DamageType type, LivingEntity attacker, ItemStack weapon) {}

    private static final Map<UUID, Context> CACHE = new ConcurrentHashMap<>();

    public static void record(UUID victim, DamageType type, LivingEntity attacker) {
        ItemStack weapon = null;
        if (attacker instanceof Player p) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (!held.getType().isAir()) weapon = held;
        }
        CACHE.put(victim, new Context(type, attacker, weapon));
    }

    /** Returns and clears the cached context for {@code victim} — call once per death, from PlayerDeathEvent. */
    public static Context consume(UUID victim) {
        return CACHE.remove(victim);
    }

    /** Evicts a player's entry without reading it — call on quit to avoid unbounded growth. */
    public static void clear(UUID victim) {
        CACHE.remove(victim);
    }
}
