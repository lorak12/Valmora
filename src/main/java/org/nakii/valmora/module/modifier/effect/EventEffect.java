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
 * <p>Same dispatch-wiring caveat as {@link AbilityEffect}: parsed and stored, PASSIVE-adjacent
 * firing is not yet wired for arbitrary triggers beyond what {@link
 * org.nakii.valmora.module.modifier.ModifierEngine} explicitly drives (currently modifier
 * application/removal only — see the class javadoc there).
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
