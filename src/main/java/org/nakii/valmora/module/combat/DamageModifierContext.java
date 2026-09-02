package org.nakii.valmora.module.combat;

public class DamageModifierContext {
    private double baseDamage;
    private double strength;
    private double critChance;
    private double critDamage;
    private double defense;
    private double damageMultiplier = 1.0;
    /** Additive attacker-side defense reduction, as a percent of the victim's defense stat
     *  (0-100+), applied before the standard {@code 100/(100+defense)} mitigation formula. Added
     *  for the enchant overhaul's {@code combat.modify-attack.modifiers.defense-shred-percent} —
     *  kept separate from {@link #damageMultiplier} since attacker and victim enchants share this
     *  one context instance per hit and must not clobber each other's contribution. */
    private double defenseShredPercent = 0.0;
    /** Additive defender-side flat damage reduction (0-100+), applied as an extra multiplicative
     *  step alongside defense mitigation. Added for {@code combat.modify-defend.modifiers.damage-reduction-percent}. */
    private double damageReductionPercent = 0.0;
    /** Multiplicative knockback scaling for this hit (1.0 = vanilla knockback unchanged, 0 = fully
     *  suppressed) — VANILLA_CONTROL_AUDIT.md §14 Medium #22. Composed the same way as
     *  {@link #damageMultiplier}: each contributor multiplies the running total. Read by
     *  {@code DamageResult#getKnockbackMultiplier()} after the hit is calculated and consumed by
     *  {@code CombatKnockbackListener} against the {@code EntityKnockbackEvent} vanilla fires for
     *  the same physical hit. */
    private double knockbackMultiplier = 1.0;
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

    public double getDefenseShredPercent() {
        return defenseShredPercent;
    }

    public void setDefenseShredPercent(double defenseShredPercent) {
        this.defenseShredPercent = defenseShredPercent;
    }

    /** Adds to the current value — the composition convention multiple stacked enchants should use. */
    public void addDefenseShredPercent(double amount) {
        this.defenseShredPercent += amount;
    }

    public double getDamageReductionPercent() {
        return damageReductionPercent;
    }

    public void setDamageReductionPercent(double damageReductionPercent) {
        this.damageReductionPercent = damageReductionPercent;
    }

    public void addDamageReductionPercent(double amount) {
        this.damageReductionPercent += amount;
    }

    public double getKnockbackMultiplier() {
        return knockbackMultiplier;
    }

    public void setKnockbackMultiplier(double knockbackMultiplier) {
        this.knockbackMultiplier = knockbackMultiplier;
    }
}
