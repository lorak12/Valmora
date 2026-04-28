package org.nakii.valmora.module.combat;

public class DamageModifierContext {
    private double baseDamage;
    private double strength;
    private double critChance;
    private double critDamage;
    private double defense;
    private double damageMultiplier = 1.0;
    private final DamageType damageType;

    public DamageModifierContext(double baseDamage, double strength, double critChance, double critDamage, double defense, DamageType damageType) {
        this.baseDamage = baseDamage;
        this.strength = strength;
        this.critChance = critChance;
        this.critDamage = critDamage;
        this.defense = defense;
        this.damageType = damageType;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    public double getStrength() {
        return strength;
    }

    public void setStrength(double strength) {
        this.strength = strength;
    }

    public double getCritChance() {
        return critChance;
    }

    public void setCritChance(double critChance) {
        this.critChance = critChance;
    }

    public double getCritDamage() {
        return critDamage;
    }

    public void setCritDamage(double critDamage) {
        this.critDamage = critDamage;
    }

    public double getDefense() {
        return defense;
    }

    public void setDefense(double defense) {
        this.defense = defense;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public DamageType getDamageType() {
        return damageType;
    }
}
