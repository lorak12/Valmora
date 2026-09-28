package org.nakii.valmora.module.fishing.flow;

import org.bukkit.Location;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.pipeline.FeedbackSteps;

/** State of one {@code fishing.bite} flow run: where the bobber is. */
public final class BiteState implements FeedbackSteps.Located {

    private final Location hook;

    public BiteState(Location hook) {
        this.hook = hook;
    }

    @Override
    public Location feedbackLocation() {
        return hook;
    }

    @Override
    public void publish(ExecutionContext ctx) {
        // nothing script-visible beyond the context's own location
    }
}
