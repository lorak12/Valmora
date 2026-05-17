package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.StatManager;

public class EfficiencyLogic implements EnchantmentLogic {

    @Override
    public void applyStats(LivingEntity entity, int level, StatManager statManager) {
        if (entity instanceof Player) {
            statManager.addModifier(ValmoraAPI.getInstance().getSystemStats().getMiningSpeed(), 50.0 * level);
        }
    }
}
