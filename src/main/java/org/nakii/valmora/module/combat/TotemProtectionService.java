package org.nakii.valmora.module.combat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.stat.StatManager;

/**
 * VANILLA_CONTROL_AUDIT.md §9 — Totem of Undying is silently non-functional against Valmora's own
 * damage pipeline. Vanilla's totem-death-protection check lives inside NMS's damage-application
 * path (roughly {@code LivingEntity.actuallyHurt}/{@code die}), which never runs here: player health
 * is fully virtualized ({@code PlayerState.currentHealth}), the real {@link org.bukkit.event.entity.EntityDamageEvent}
 * damage is always zeroed by {@link CombatListener}, and death is force-triggered by a direct
 * {@code player.setHealth(0)} call ({@code PlayerManager.syncVisualHealth}) that bypasses vanilla's
 * totem check entirely.
 *
 * <p>This resolves totem protection manually — called from {@link DamageApplier} <b>before</b> it
 * reduces virtual health into fatal territory — and fires a real {@link EntityResurrectEvent} so
 * other plugins observing that event still see it and can cancel it. Known limitation: since the
 * actual vanilla resurrect code path never runs, the client's totem pop-up animation (driven
 * server-side by that same NMS path) does not play — only the sound and the resulting
 * effects/health are reproduced. Fixing that would require raw packet injection (PacketEvents is
 * already a hard dependency for NPC dialogue — see AGENTS.md §14.1) and was judged not worth it for
 * a one-off visual flourish; revisit if it becomes a real complaint.
 */
public final class TotemProtectionService {

    private TotemProtectionService() {}

    /**
     * @param incomingDamage the damage about to be applied to {@code state.getCurrentHealth()}
     * @return true if a totem consumed this hit — the caller must not apply the fatal damage/death,
     *         state/visual health has already been synced. False if there's no totem, the hit isn't
     *         actually fatal, or another plugin cancelled the synthetic {@link EntityResurrectEvent}.
     */
    public static boolean tryProtect(Player player, PlayerState state, StatManager stats, double incomingDamage) {
        if (state.getCurrentHealth() - incomingDamage > 0) return false; // not fatal

        EquipmentSlot totemSlot = findTotemSlot(player);
        if (totemSlot == null) return false;

        EntityResurrectEvent event = new EntityResurrectEvent(player, totemSlot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        consumeTotem(player, totemSlot);

        // Vanilla leaves the player at 1 HP out of a 20-HP bar post-totem — reproduced as the
        // equivalent fraction of the player's actual max-health stat, not a literal 1.0.
        String healthId = ValmoraAPI.getInstance().getSystemStats().getHealth();
        double maxHealth = stats.getStat(healthId);
        double oneHpEquivalent = Math.max(maxHealth / 20.0, 0.01);
        state.reduceHealth(state.getCurrentHealth()); // zero out, then heal to the post-totem sliver
        state.heal(oneHpEquivalent, stats);

        applyTotemEffects(player);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        ValmoraAPI.getInstance().getPlayerManager().syncVisualHealth(player, state, stats);
        return true;
    }

    private static EquipmentSlot findTotemSlot(Player player) {
        if (player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) return EquipmentSlot.HAND;
        if (player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) return EquipmentSlot.OFF_HAND;
        return null;
    }

    private static void consumeTotem(Player player, EquipmentSlot slot) {
        boolean mainHand = slot == EquipmentSlot.HAND;
        ItemStack item = mainHand ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        item.setAmount(item.getAmount() - 1);
        if (mainHand) player.getInventory().setItemInMainHand(item);
        else player.getInventory().setItemInOffHand(item);
    }

    /** Vanilla totem effect table: Regeneration II (900 ticks), Absorption I / Fire Resistance (800 ticks). */
    private static void applyTotemEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 800, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0, false, true, true));
    }
}
