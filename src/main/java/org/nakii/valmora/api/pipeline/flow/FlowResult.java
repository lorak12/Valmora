package org.nakii.valmora.api.pipeline.flow;

/**
 * Outcome of running a flow: every step ran, one step stopped it ({@link #stoppedAt()} names it),
 * or the flow was never loaded so nothing ran at all ({@link #skipped()} — the caller should fall
 * back to vanilla behaviour). Callers map a stop onto their own result type — e.g. a cancelled
 * hit, {@code BreakResult.INTERRUPTED}, "nothing caught".
 */
public record FlowResult(String stoppedAt, boolean skipped) {

    public static final FlowResult COMPLETED = new FlowResult(null, false);
    public static final FlowResult SKIPPED = new FlowResult(null, true);

    public static FlowResult stopped(String stepId) {
        return new FlowResult(stepId, false);
    }

    public boolean completed() {
        return stoppedAt == null && !skipped;
    }

    public boolean stopped() {
        return stoppedAt != null;
    }
}
