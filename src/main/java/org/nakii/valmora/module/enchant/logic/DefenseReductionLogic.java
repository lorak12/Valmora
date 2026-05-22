package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.StatManager;

public class DefenseReductionLogic implements EnchantmentLogic {

    private final double percentPerLevel;

    public DefenseReductionLogic(double percentPerLevel) {
        this.percentPerLevel = percentPerLevel;
    }

    @Override
    public void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {
        // Reduces victim's effective defense by (percentPerLevel * level)% of its current value
        double reduction = context.getDefense() * (percentPerLevel / 100.0) * level;
        context.setDefense(Math.max(0, context.getDefense() - reduction));
    }
}
