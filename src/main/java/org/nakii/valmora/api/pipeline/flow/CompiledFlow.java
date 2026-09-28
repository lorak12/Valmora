package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.util.DebugManager;

import java.util.List;

/**
 * An immutable, load-time-built flow: a plain step array, run in order until a step stops it. A
 * reload builds a new instance and swaps it in atomically ({@link FlowSpec#install}), so a run
 * already in progress finishes on the array it started with.
 */
public final class CompiledFlow {

    private static final String DEBUG = "script";

    private final String key;
    private final FlowStep[] steps;
    private final String source;

    /**
     * @param key    {@code <domain>.<flow>}, e.g. {@code fishing.catch}
     * @param source where it came from — {@code "file"} or {@code "bundled"} — shown by the command
     */
    public CompiledFlow(String key, List<FlowStep> steps, String source) {
        this.key = key;
        this.steps = steps.toArray(new FlowStep[0]);
        this.source = source;
    }

    public FlowResult run(ExecutionContext ctx, FlowState state) {
        boolean debug = DebugManager.isEnabled(DEBUG);
        for (FlowStep step : steps) {
            long start = debug ? System.nanoTime() : 0L;
            StepOutcome outcome = step.run(ctx, state);
            if (debug) {
                DebugManager.log(DEBUG, "flow '" + key + "' step '" + step.id() + "' (" + step.kind() + ") -> "
                        + outcome + " in " + (System.nanoTime() - start) / 1000 + "us");
            }
            if (outcome == StepOutcome.STOP) {
                return FlowResult.stopped(step.id());
            }
        }
        return FlowResult.COMPLETED;
    }

    public String key() {
        return key;
    }

    public String source() {
        return source;
    }

    public List<FlowStep> steps() {
        return List.of(steps);
    }
}
