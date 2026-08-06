package org.nakii.valmora.api.pipeline;

/**
 * Outcome of a single {@link PipelineStage} run against a {@link HookBus} insertion point.
 */
public enum StageResult {
    /** Keep running the remaining stages registered at this insertion point. */
    CONTINUE,
    /** Stop running further stages at this insertion point and signal the caller to abort. */
    INTERRUPT
}
