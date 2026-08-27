package org.nakii.valmora.module.modifier.effect;

import org.nakii.valmora.module.modifier.value.ValueResolver;
import org.nakii.valmora.module.script.condition.ConditionGroup;

/**
 * Contributes to an existing stat id (docs/Valmora_Modifier_Framework_Design.docx §7/§8) — no
 * modifier-specific stat implementation, per CLAUDE.md §7.3 "reuse existing stat system".
 */
public class StatEffect implements ModifierEffect {

    public enum Operation { ADD, MULTIPLY }

    private final String stat;
    private final Operation operation;
    private final ValueResolver value;
    private final ConditionGroup conditions;

    public StatEffect(String stat, Operation operation, ValueResolver value, ConditionGroup conditions) {
        this.stat = stat;
        this.operation = operation;
        this.value = value;
        this.conditions = conditions;
    }

    public String getStat() { return stat; }
    public Operation getOperation() { return operation; }
    public ValueResolver getValue() { return value; }

    @Override
    public String getType() { return "STAT"; }

    @Override
    public ConditionGroup getConditions() { return conditions; }
}
