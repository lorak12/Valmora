package org.nakii.valmora.module.enchant;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.stat.StatManager;

public class SharpnessLogic implements EnchantmentLogic {

    @Override
    public void applyStats(Player player, int level, StatManager stats) {
        double damageBonus = 5.0 * level;
        stats.addStat(player, org.nakii.valmora.module.stat.Stat.DAMAGE, damageBonus);
    }

    @Override
    public void onAttack(DamageResult result, int level) {
        double multiplier = 1.0 + (0.05 * level);
        double extraDamage = result.getFinalDamage() * (multiplier - 1.0);
    }
}