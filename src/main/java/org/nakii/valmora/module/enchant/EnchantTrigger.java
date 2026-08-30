package org.nakii.valmora.module.enchant;

/**
 * Named dispatch points a {@code triggers:} YAML block can bind actions to — each compiles into
 * its own {@code "enchant:<enchantId>:<trigger>"} {@link org.nakii.valmora.api.pipeline.HookBus}
 * point (see {@link EnchantTriggerStage}), dispatched by {@link EnchantDispatcher} /
 * {@link EnchantKillListener}.
 *
 * <p>Distinct from {@code combat.modify-attack}/{@code modify-defend}, which are a separate
 * numeric-modifier mechanism (see {@link EnchantCombatHook}), not an action-list trigger.
 */
public enum EnchantTrigger {
    /** After a hit this enchant's holder (wielding the weapon) dealt has been fully resolved. */
    ON_ATTACK_POST,
    /** After a hit this enchant's holder (wearing the armor) took has been fully resolved. */
    ON_DEFEND_POST,
    /** The wielder of this enchant's weapon killed something. */
    ON_KILL,
    /** The wearer of this enchant's armor/item died. */
    ON_DEATH
}
