package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * One compiled entry of a {@link CompiledFlow}: a built-in (Java) step, an anchor (a
 * {@code HookBus} point) or a script step.
 */
public interface FlowStep {

    /** Unique-per-flow id (defaults to the built-in/anchor name). */
    String id();

    /** Human-readable kind for {@code /valmora pipeline flows}: {@code builtin:x}, {@code anchor:x}, {@code script}. */
    String kind();

    /** Whether this step has its own {@code conditions:} gate. */
    boolean hasConditions();

    StepOutcome run(ExecutionContext ctx, FlowState state);
}
