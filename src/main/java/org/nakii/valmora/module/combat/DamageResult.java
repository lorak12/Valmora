package org.nakii.valmora.module.combat;


import org.bukkit.entity.LivingEntity;

import org.nakii.valmora.api.ValmoraAPI;

public class DamageResult {
    private final double finalDamage;
    private final DamageType damageType;
    private final boolean isCritical;
    private final LivingEntity attacker;
    private final LivingEntity victim;

    public DamageResult(double finalDamage, DamageType damageType, boolean isCritical, LivingEntity attacker, LivingEntity victim) {
        this.finalDamage = finalDamage;
        this.damageType = damageType;
        this.isCritical = isCritical;
        this.attacker = attacker;
        this.victim = victim;
    }

    public void apply() {
        DamageApplier applier = new DamageApplier(this, (org.bukkit.plugin.Plugin) ValmoraAPI.getInstance());
        applier.applyDamage();
    }

    public double getFinalDamage() {
        return finalDamage;
    }

    public DamageType getDamageType() {
        return damageType;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public LivingEntity getAttacker() {
        return attacker;
    }

    public LivingEntity getVictim() {
        return victim;
    }
}
