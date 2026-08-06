package org.nakii.valmora.api.pipeline;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * A single named stage registered at a {@link HookBus} insertion point. Implementations may be
 * compiled from YAML (see {@link CompiledPipelineStage}) or written directly in Java by an addon
 * plugin (see {@link PipelineHook}).
 */
public interface PipelineStage {

    /** Unique-per-point identifier, used for logging and for {@link HookBus#unregisterHook}. */
    String getId();

    /**
     * Runs this stage against the given context.
     * @return {@link StageResult#INTERRUPT} to stop the remaining stages at this insertion point;
     *         {@link StageResult#CONTINUE} otherwise.
     */
    StageResult run(ExecutionContext context);
}
