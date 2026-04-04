package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.combat.DamageCalculator;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.item.AbilityMechanic;

public class DamageMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "damage";
    }

    @Override
    public void execute(ExecutionContext context) {
        LivingEntity actualTarget = context.getTarget().orElse(null);
        if (actualTarget == null) return;

        double amount = context.getDouble("amount", 1.0);
        String damageTypeStr = context.getString("type", "MAGIC").toUpperCase();
        DamageType damageType;
        try {
            damageType = DamageType.valueOf(damageTypeStr);
        } catch (IllegalArgumentException e) {
            damageType = DamageType.MAGIC;
        }

        DamageResult result = DamageCalculator.calculateDamage(context.getCaster(), actualTarget, damageType, amount);
        result.apply();

        ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(result);
    }
}
