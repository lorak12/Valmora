package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.CompiledPipelineStage;
import org.nakii.valmora.api.pipeline.StageResult;

/**
 * A script step placed directly in a flow ({@code id} + {@code conditions}/{@code on-pass}/
 * {@code on-fail}) — the same {@link CompiledPipelineStage} a legacy {@code stages:} entry
 * compiles to, but run at its own position. Calling {@code interrupt} stops the flow here.
 * A throwing script is logged and skipped, same as {@code HookBus} treats a broken stage.
 */
public final class ScriptFlowStep implements FlowStep {

    private final CompiledPipelineStage stage;
    private final boolean conditional;

    public ScriptFlowStep(CompiledPipelineStage stage, boolean conditional) {
        this.stage = stage;
        this.conditional = conditional;
    }

    @Override
    public String id() {
        return stage.id();
    }

    @Override
    public String kind() {
        return "script";
    }

    @Override
    public boolean hasConditions() {
        return conditional;
    }

    @Override
    public StepOutcome run(ExecutionContext ctx, FlowState state) {
        FlowState.expose(ctx, state);
        ctx.remove(AnchorFlowStep.INTERRUPT_KEY);
        StageResult result;
        try {
            result = stage.run(ctx);
        } catch (Exception e) {
            Valmora plugin = Valmora.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("[Pipeline] Script step '" + stage.id() + "' threw an exception, skipping it: " + e);
            }
            return StepOutcome.CONTINUE;
        }
        if (result == StageResult.INTERRUPT) {
            ctx.remove(AnchorFlowStep.INTERRUPT_KEY);
            return StepOutcome.STOP;
        }
        return StepOutcome.CONTINUE;
    }
}
