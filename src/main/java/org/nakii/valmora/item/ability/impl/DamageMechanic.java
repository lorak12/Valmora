package org.nakii.valmora.item.ability.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.combat.DamageApplier;
import org.nakii.valmora.combat.DamageResult;
import org.nakii.valmora.combat.DamageType;
import org.nakii.valmora.item.ability.AbilityMechanic;

public class DamageMechanic implements AbilityMechanic{
    
    @Override
    public String getId() {
        return "DAMAGE";
    }

    @Override
    public void execute(Player caster, LivingEntity target, ConfigurationSection params) {
        String targetType = params.getString("target", "@target");
        LivingEntity actualTarget = targetType.equalsIgnoreCase("@player") ? caster : target;

        if(actualTarget == null) return;

        double damage = params.getDouble("damage", 1.0);
        String damageTypeStr = params.getString("damage-type", "MAGIC").toUpperCase();
        DamageType damageType;
        try {
            damageType = DamageType.valueOf(damageTypeStr);
        } catch (IllegalArgumentException e) {
            damageType = DamageType.MAGIC;
        }

        DamageResult result = new DamageResult(damage, damageType, false, caster, actualTarget);

        DamageApplier applier = new DamageApplier(result, Valmora.getInstance());
        applier.applyDamage();

        Valmora.getInstance().getDamageIndicatorManager().spawnIndicator(result);
    }
}
