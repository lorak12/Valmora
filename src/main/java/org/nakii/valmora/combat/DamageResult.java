package org.nakii.valmora.combat;


import org.bukkit.entity.LivingEntity;

public class DamageResult {
    double finalDamage;
    DamageType damageType;
    boolean isCritical;
    LivingEntity attacker;
    LivingEntity victim;

    public DamageResult(double finalDamage, DamageType damageType, boolean isCritical, LivingEntity attacker, LivingEntity victim) {
        this.finalDamage = finalDamage;
        this.damageType = damageType;
        this.isCritical = isCritical;
        this.attacker = attacker;
        this.victim = victim;
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
