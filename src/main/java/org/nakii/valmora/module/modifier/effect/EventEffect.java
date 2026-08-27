package org.nakii.valmora.module.modifier.effect;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.List;

/**
 * Dispatches existing Script DSL event strings on a trigger (docs/
 * Valmora_Modifier_Framework_Design.docx §8), e.g. {@code ON_KILL -> "notify <red>Bloodlust!"}.
 * Reuses {@link org.nakii.valmora.module.script.event.EventParser} — no second action language.
 *
 * <p>Dispatched via {@code ModifierEngine#getGrantedEventActions} + {@code
 * AbilityExecutor#fireModifiersForItem}/{@code #fireModifiersHeld}, at the same call sites as
 * {@link AbilityEffect}. Unlike an ability, there's no cooldown/mana gate — the actions fire every
 * time their trigger occurs (each action string may of course include its own {@code condition ...}
 * step, or the effect's own {@code conditions:} can gate the whole thing).
 */
public class EventEffect implements ModifierEffect {

    private final AbilityTrigger trigger;
    private final List<CompiledEvent> actions;
    private final ConditionGroup conditions;

    public EventEffect(AbilityTrigger trigger, List<CompiledEvent> actions, ConditionGroup conditions) {
        this.trigger = trigger;
        this.actions = actions;
        this.conditions = conditions;
    }

    public AbilityTrigger getTrigger() { return trigger; }
    public List<CompiledEvent> getActions() { return actions; }

    @Override
    public String getType() { return "EVENT"; }

    @Override
    public ConditionGroup getConditions() { return conditions; }
}
