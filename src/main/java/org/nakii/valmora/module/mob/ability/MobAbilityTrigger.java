package org.nakii.valmora.module.mob.ability;

/**
 * Defines when a {@link MobAbility} fires. Most mobs have no abilities; these triggers are for bosses.
 */
public enum MobAbilityTrigger {
    /** Fires on a repeating interval (with an optional random chance) driven by the boss task. */
    ON_TIMER,
    /** Fires once when the mob's health drops below a configured percentage. */
    ON_HEALTH,
    /** Fires when the mob deals melee/projectile damage to a target. */
    ON_ATTACK,
    /** Fires when the mob takes damage. */
    ON_DAMAGED,
    /** Fires once when the mob is spawned. */
    ON_SPAWN,
    /** Fires once when the mob dies. */
    ON_DEATH
}
