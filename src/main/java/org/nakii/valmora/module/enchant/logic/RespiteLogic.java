package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.StatManager;

/**
 * "Respite" (`valmora:respite`) — a {@code health_regen} bonus per level, but only while the
 * player is out of combat (unlike the plain {@code valmora:stat_bonus} shape used by
 * growth/fortune/efficiency/protection, which is why this needed its own class instead of a
 * factory-configured {@link StatBonusLogic}). Added 2026-08-07 to wire up one of the 7
 * previously-inert shipped enchants.
 */
public class RespiteLogic implements EnchantmentLogic {

    private final double perLevel;

    public RespiteLogic(double perLevel) {
        this.perLevel = perLevel;
    }

    @Override
    public void applyStats(LivingEntity entity, int level, StatManager statManager) {
        if (entity instanceof Player player) {
            var vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
            var profile = vp != null ? vp.getActiveProfile() : null;
            if (profile != null && profile.getPlayerState().isInCombat()) return;
        }
        statManager.addModifier(ValmoraAPI.getInstance().getSystemStats().getHealthRegen(), perLevel * level);
    }
}
