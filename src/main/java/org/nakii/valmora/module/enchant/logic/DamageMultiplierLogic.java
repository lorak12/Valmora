package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.enchant.EnchantmentLogic;
import org.nakii.valmora.module.stat.StatManager;

public class DamageMultiplierLogic implements EnchantmentLogic {

    // null means "apply to any damage type"
    private final DamageType damageType;
    private final double percentPerLevel;

    public DamageMultiplierLogic(String type, double percentPerLevel) {
        DamageType parsed = null;
        if (!type.equalsIgnoreCase("ANY")) {
            try { parsed = DamageType.valueOf(type.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        this.damageType = parsed;
        this.percentPerLevel = percentPerLevel;
    }

    @Override
    public void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {
        if (damageType == null || context.getDamageType() == damageType) {
            context.setDamageMultiplier(context.getDamageMultiplier() * (1.0 + (percentPerLevel / 100.0) * level));
        }
    }
}
