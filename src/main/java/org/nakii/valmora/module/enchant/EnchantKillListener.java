package org.nakii.valmora.module.enchant;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

/**
 * Fires {@link EnchantTrigger#ON_KILL} for the killer's main-hand weapon on any kill (vanilla mob,
 * custom mob, or PvP) — its own listener rather than piggy-backing on {@code MobDeathListener}'s
 * {@code combat:on_death} point (custom-mobs-only, per its {@code mobId != null} guard) or {@code
 * module.item.AbilityTriggerListener}'s {@code onKill} (a different, item-ability-owned dispatch).
 * Three independent listeners on the same {@link EntityDeathEvent} is the established pattern in
 * this codebase (see also {@code MobDeathListener}, {@code AbilityTriggerListener}) — each owns a
 * different trigger dispatch, so this isn't a double-fire risk.
 *
 * <p>Also fires {@link EnchantTrigger#ON_DEATH} for everything a dying player holds and wears
 * (added 2026-09-24 — the trigger was declared, and accepted in enchant YAML, but never fired).
 */
public class EnchantKillListener implements Listener {

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null || !EnchantmentHelper.hasValmoraEnchants(weapon)) return;

        var ctx = new SimpleExecutionContext(killer, event.getEntity(), event.getEntity().getLocation(), null);
        EnchantDispatcher.fireTriggerForItem(weapon, EnchantTrigger.ON_KILL, ctx);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        var inventory = player.getInventory();
        java.util.List<ItemStack> carried = new java.util.ArrayList<>();
        carried.add(inventory.getItemInMainHand());
        carried.add(inventory.getItemInOffHand());
        carried.addAll(java.util.Arrays.asList(inventory.getArmorContents()));

        for (ItemStack item : carried) {
            if (item == null || !EnchantmentHelper.hasValmoraEnchants(item)) continue;
            var ctx = new SimpleExecutionContext(player, player.getKiller(), player.getLocation(), null);
            EnchantDispatcher.fireTriggerForItem(item, EnchantTrigger.ON_DEATH, ctx);
        }
    }
}
