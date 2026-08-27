package org.nakii.valmora.module.modifier.effect;

import org.nakii.valmora.module.item.AbilityDefinition;
import org.nakii.valmora.module.script.condition.ConditionGroup;

/**
 * Grants an ordinary {@link AbilityDefinition} (docs/Valmora_Modifier_Framework_Design.docx §9/§17)
 * — no second ability/mechanic language: the same trigger dispatcher, {@code MechanicRegistry},
 * {@code TargetResolver}, cooldowns, and mana as item abilities (CLAUDE.md §7.3, design doc §23).
 *
 * <p>Only inline {@code definition:} is currently supported — referencing an existing ability by id
 * would additionally require a global ability registry, which doesn't exist yet in this codebase
 * (abilities currently live only inside {@code ItemDefinition}/mob definitions); see
 * docs/HSB_DECOUPLING_BACKLOG.md-style follow-up notes in the modifier framework backlog doc.
 *
 * <p><b>Trigger dispatch:</b> {@code PASSIVE} modifier-granted abilities are fired from {@code
 * StatManager.recalculateStats} alongside item-defined passive abilities (see {@code
 * ModifierEngine#applyPassiveAbilities}). Every other trigger (ON_HIT, RIGHT_CLICK, ON_KILL, SNEAK,
 * ON_SHOOT, ON_DAMAGE_TAKEN, ON_TELEPORT, EQUIP, UNEQUIP) is dispatched through {@code
 * ModifierEngine#getGrantedAbilities} + {@code AbilityExecutor#fireModifiersForItem}/{@code
 * #fireModifiersHeld}, wired into the same call sites as item-defined abilities ({@code
 * AbilityTriggerListener}, {@code CombatListener}) — full cooldown/mana/condition gating applies,
 * same as an item ability.
 */
public class AbilityEffect implements ModifierEffect {

    private final AbilityDefinition definition;
    private final ConditionGroup conditions;

    public AbilityEffect(AbilityDefinition definition, ConditionGroup conditions) {
        this.definition = definition;
        this.conditions = conditions;
    }

    public AbilityDefinition getDefinition() { return definition; }

    @Override
    public String getType() { return "ABILITY"; }

    @Override
    public ConditionGroup getConditions() { return conditions; }
}
