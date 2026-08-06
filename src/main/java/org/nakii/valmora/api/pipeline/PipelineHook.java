package org.nakii.valmora.api.pipeline;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Java-side counterpart to a YAML-compiled pipeline stage. Register one via
 * {@link HookBus#registerHook(String, String, PipelineHook)} to react to a named insertion point
 * (e.g. {@code "combat:pre_damage"}) without writing script DSL — the intended extension point for
 * addon plugins (see CLAUDE.md / docs/COMBAT_PIPELINE_ANALYSIS.md §3.16).
 */
@FunctionalInterface
public interface PipelineHook {

    /**
     * @return {@link StageResult#INTERRUPT} to stop the remaining stages at this insertion point;
     *         {@link StageResult#CONTINUE} otherwise.
     */
    StageResult handle(ExecutionContext context);
}
