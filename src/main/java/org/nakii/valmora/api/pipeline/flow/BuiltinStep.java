package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.api.execution.ExecutionContext;

/** A compiled built-in step: a piece of what used to be one hardcoded Java method. */
@FunctionalInterface
public interface BuiltinStep<S extends FlowState> {

    StepOutcome run(ExecutionContext ctx, S state);
}
