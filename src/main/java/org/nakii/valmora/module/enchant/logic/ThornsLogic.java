package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.enchant.EnchantmentLogic;

/**
 * "Thorns" (`valmora:thorns`) — a per-level chance to reflect true damage back at the attacker
 * on a successful hit taken. Added 2026-08-07 to wire up one of the 7 previously-inert shipped
 * enchants. True damage, bypassing the attacker's own defense (matches the shipped description's
 * "deal 1 damage back") — same direct-health-mutation pattern as `DamageAlchemyEffect`/
 * `PoisonAlchemyEffect` rather than routing back through the full damage pipeline (which would
 * itself re-trigger this same hook on the original attacker's gear, recursing).
 */
public class ThornsLogic implements EnchantmentLogic {

    private final double chancePercent;
    private final double reflectDamage;

    public ThornsLogic(double chancePercent, double reflectDamage) {
        this.chancePercent = chancePercent;
        this.reflectDamage = reflectDamage;
    }

    @Override
    public void onPostDefend(DamageResult result, LivingEntity attacker, LivingEntity victim, int level) {
        if (result.isImmune() || attacker == null || attacker.isDead()) return;
        if (Math.random() * 100 >= chancePercent) return;

        if (attacker instanceof Player player) {
            var api = ValmoraAPI.getInstance();
            var vp = api.getPlayerManager().getSession(player.getUniqueId());
            var profile = vp != null ? vp.getActiveProfile() : null;
            if (profile == null) return;
            profile.getPlayerState().reduceHealth(reflectDamage);
            api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
        } else {
            attacker.setHealth(Math.max(0, attacker.getHealth() - reflectDamage));
        }
    }
}
