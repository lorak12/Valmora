package org.nakii.valmora.module.enchant;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;

public class GrowthLogic implements EnchantmentLogic {

    @Override
    public void applyStats(Player player, int level, StatManager stats) {
        double healthBonus = 10.0 * level;
        stats.addStat(player, Stat.HEALTH, healthBonus);
    }
}