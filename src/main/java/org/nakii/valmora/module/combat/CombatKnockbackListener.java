package org.nakii.valmora.module.combat;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

/**
 * VANILLA_CONTROL_AUDIT.md §14 Medium #22 — "Knockback model": vanilla's own knockback (accel
 * vector, KB-resistance attribute) already runs correctly on its own (a mob's
 * {@code MobDefinition.knockback-resistance} feeds the vanilla {@code KNOCKBACK_RESISTANCE}
 * attribute directly via {@code MobFactory}, and any admin-configured player stat mapped to a
 * vanilla attribute — including {@code generic.knockback_resistance} — already flows through
 * {@code StatModule.recalculateAttributes}'s generic branch) — but nothing in Valmora could
 * previously suppress or scale a single hit's knockback (attacker "no-knockback" abilities,
 * defender "steadfast" enchants), since no listener observed
 * {@link io.papermc.paper.event.entity.EntityKnockbackEvent} at all.
 *
 * <p>Only acts on {@link EntityKnockbackEvent.Cause#ENTITY_ATTACK} — the melee-hit case
 * {@link CombatListener}/{@link DamageCalculator} actually compute a multiplier for.
 * Explosion/shield-block/sweep/push knockback are left exactly as vanilla produces them.
 */
public class CombatKnockbackListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (event.getCause() != EntityKnockbackEvent.Cause.ENTITY_ATTACK) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Double multiplier = KnockbackModifierTracker.consume(victim.getUniqueId());
        if (multiplier == null) return; // no Valmora hit pending for this victim — leave vanilla untouched

        if (multiplier <= 0.0) {
            event.setCancelled(true);
            return;
        }
        Vector kb = event.getKnockback();
        event.setKnockback(kb.multiply(multiplier));
    }
}
