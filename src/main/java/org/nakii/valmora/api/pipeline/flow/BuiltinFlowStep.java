package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

/**
 * A {@code - builtin: <name>} entry. When its {@code conditions:} are false the step is skipped and
 * the flow continues — a condition gates a built-in, it never fails the flow.
 */
public final class BuiltinFlowStep<S extends FlowState> implements FlowStep {

    private final String id;
    private final String name;
    private final Condition conditions;
    private final BuiltinStep<S> step;

    public BuiltinFlowStep(String id, String name, Condition conditions, BuiltinStep<S> step) {
        this.id = id;
        this.name = name;
        this.conditions = conditions;
        this.step = step;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String kind() {
        return "builtin:" + name;
    }

    @Override
    public boolean hasConditions() {
        return conditions != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StepOutcome run(ExecutionContext ctx, FlowState state) {
        if (conditions != null) {
            FlowState.expose(ctx, state);
            if (!conditions.evaluate(ctx)) return StepOutcome.CONTINUE;
        }
        return step.run(ctx, (S) state);
    }
}
