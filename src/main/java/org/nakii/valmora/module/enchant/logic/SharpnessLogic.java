package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.StatManager;

public class SharpnessLogic implements EnchantmentLogic {

    @Override
    public void applyStats(LivingEntity entity, int level, StatManager profile) {
        // Passive stat removed. Sharpness is a pre-hit multiplier only.
    }

    @Override
    public void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {
        if (context.getDamageType() == DamageType.MELEE) {
            double multiplier = 1.0 + (0.05 * level);
            context.setDamageMultiplier(context.getDamageMultiplier() * multiplier);
        }
    }
}
