package org.nakii.valmora.api.pipeline;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.script.event.ConditionAbortException;

/**
 * A {@link PipelineStage} compiled from a YAML stage definition: a condition group plus
 * pass/fail compiled event lists, using the same {@code Condition}/{@code CompiledEvent}
 * primitives as GUI event blocks and abilities (see {@code GuiEventBlock}). "Interrupting" the
 * pipeline is expressed the same way any other DSL event mutates state — the built-in
 * {@code interrupt} event (see {@code InterruptEventFactory}) sets the {@code pipeline:interrupted}
 * attachment on the context, which this stage checks after running its actions.
 *
 * <p>A mid-list {@code condition} event inside {@code onPass} throwing {@link ConditionAbortException}
 * is handled the same way it is everywhere else in the engine (GUI event blocks, abilities): it
 * falls back to running {@code onFail}. Unlike {@link org.nakii.valmora.module.gui.GuiEventBlockStage}
 * (a single gate per insertion point), this does <b>not</b> report {@link StageResult#INTERRUPT} —
 * a pipeline point is a list of independent stages, so one stage's own abort should not by itself
 * stop the rest of the list from running. Use the explicit {@code interrupt} event for that.
 */
public record CompiledPipelineStage(
        String id,
        Condition conditions,
        CompiledEvent onPass,
        CompiledEvent onFail
) implements PipelineStage {

    private static final String INTERRUPT_KEY = "pipeline:interrupted";

    @Override
    public String getId() {
        return id;
    }

    @Override
    public StageResult run(ExecutionContext context) {
        if (!conditions.evaluate(context)) {
            onFail.execute(context);
            return StageResult.CONTINUE;
        }
        try {
            onPass.execute(context);
        } catch (ConditionAbortException aborted) {
            onFail.execute(context);
            return StageResult.CONTINUE;
        }
        return Boolean.TRUE.equals(context.get(INTERRUPT_KEY)) ? StageResult.INTERRUPT : StageResult.CONTINUE;
    }
}
