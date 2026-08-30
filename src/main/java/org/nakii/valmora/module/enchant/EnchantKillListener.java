package org.nakii.valmora.module.enchant;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
}
