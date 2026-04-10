package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

import java.util.List;

/**
 * Groups multiple conditions together with AND logic.
 */
public record ConditionGroup(List<Condition> conditions) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        if (conditions == null || conditions.isEmpty()) return true;
        return conditions.stream().allMatch(c -> c.evaluate(context));
    }
}
