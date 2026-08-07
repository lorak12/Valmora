package org.nakii.valmora.module.enchant.logic;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.enchant.EnchantmentLogic;

/**
 * "Life Steal" (`valmora:life_steal`) — heals the attacker for a percentage of their max health
 * per level on every successful hit. Added 2026-08-07 to wire up one of the 7 previously-inert
 * shipped enchants.
 */
public class LifeStealLogic implements EnchantmentLogic {

    private final double percentPerLevel;

    public LifeStealLogic(double percentPerLevel) {
        this.percentPerLevel = percentPerLevel;
    }

    @Override
    public void onPostAttack(DamageResult result, LivingEntity attacker, LivingEntity victim, int level) {
        if (result.isImmune() || result.getFinalDamage() <= 0) return;

        if (attacker instanceof Player player) {
            // Players heal through the profile's own health tracking (not vanilla getHealth(),
            // which is scaled to 10 hearts independent of the Valmora max-health stat) — same
            // pattern as HealingAlchemyEffect.
            var api = ValmoraAPI.getInstance();
            var vp = api.getPlayerManager().getSession(player.getUniqueId());
            var profile = vp != null ? vp.getActiveProfile() : null;
            if (profile == null) return;
            double heal = profile.getStatManager().getStat(api.getSystemStats().getHealth()) * (percentPerLevel / 100.0) * level;
            if (heal <= 0) return;
            profile.getPlayerState().heal(heal, profile.getStatManager());
            api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
        } else {
            var maxHealthAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr == null) return;
            double heal = maxHealthAttr.getValue() * (percentPerLevel / 100.0) * level;
            if (heal <= 0) return;
            attacker.setHealth(Math.min(attacker.getHealth() + heal, maxHealthAttr.getValue()));
        }
    }
}
