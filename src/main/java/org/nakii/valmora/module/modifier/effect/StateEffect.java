package org.nakii.valmora.module.modifier.effect;

import org.nakii.valmora.module.modifier.value.ValueResolver;
import org.nakii.valmora.module.script.condition.ConditionGroup;

/**
 * Mutates modifier-local instance state (docs/Valmora_Modifier_Framework_Design.docx §8/§13). State
 * belongs to the {@code ModifierInstance}, not the player profile (§23 hard constraint) — never
 * overloads {@code player.var.*}.
 *
 * <p>Applied once at attachment time by {@code ModifierEngine.apply(...)} for base-level (non-tier,
 * non-trigger) STATE effects, which covers the doc's declared-default-state use case. Trigger-bound
 * state mutation (the {@code ON_KILL -> STATE ADD souls} pattern from §13) additionally requires
 * non-passive ability/event trigger dispatch, which is not yet wired (see {@link AbilityEffect}).
 */
public class StateEffect implements ModifierEffect {

    public enum Operation { ADD, SET }

    private final Operation operation;
    private final String key;
    private final ValueResolver value;
    private final ConditionGroup conditions;

    public StateEffect(Operation operation, String key, ValueResolver value, ConditionGroup conditions) {
        this.operation = operation;
        this.key = key;
        this.value = value;
        this.conditions = conditions;
    }

    public Operation getOperation() { return operation; }
    public String getKey() { return key; }
    public ValueResolver getValue() { return value; }

    @Override
    public String getType() { return "STATE"; }

    @Override
    public ConditionGroup getConditions() { return conditions; }
}
