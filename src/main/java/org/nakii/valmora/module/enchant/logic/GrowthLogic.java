package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;

public class GrowthLogic implements EnchantmentLogic {

    @Override
    public void applyStats(LivingEntity entity, int level, StatManager profile) {
        if (entity instanceof Player p) {
            double healthBonus = 10.0 * level;
            profile.addModifier(Stat.HEALTH, healthBonus);
        }
    }
}