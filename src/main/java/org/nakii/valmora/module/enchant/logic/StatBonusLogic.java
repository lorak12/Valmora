package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.StatManager;

public class StatBonusLogic implements EnchantmentLogic {

    private final String statId;
    private final double perLevel;

    public StatBonusLogic(String statId, double perLevel) {
        this.statId = statId.toLowerCase();
        this.perLevel = perLevel;
    }

    @Override
    public void applyStats(LivingEntity entity, int level, StatManager statManager) {
        statManager.addModifier(statId, perLevel * level);
    }
}
