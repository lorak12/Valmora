package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.api.scripting.Condition;

/**
 * An {@code - anchor: <point>} entry: runs every stage registered at that {@code HookBus} point
 * (legacy {@code stages:} entries with a matching {@code when:}, and Java addon hooks). Costs one
 * {@link HookBus#hasStages} check when nothing is registered there.
 *
 * <p>The {@code pipeline:interrupted} flag is cleared around each anchor so an {@code interrupt}
 * at one point can't leak into the next point's stages (it used to stay set on the shared
 * context for the rest of the hit).
 */
public final class AnchorFlowStep implements FlowStep {

    static final String INTERRUPT_KEY = "pipeline:interrupted";

    private final String id;
    private final String point;
    private final HookBus bus;
    private final Condition conditions;
    private final boolean stopOnInterrupt;

    public AnchorFlowStep(String id, String point, HookBus bus, Condition conditions, boolean stopOnInterrupt) {
        this.id = id;
        this.point = point;
        this.bus = bus;
        this.conditions = conditions;
        this.stopOnInterrupt = stopOnInterrupt;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String kind() {
        return "anchor:" + point + (stopOnInterrupt ? "" : " (interrupt ignored)");
    }

    @Override
    public boolean hasConditions() {
        return conditions != null;
    }

    public String point() {
        return point;
    }

    @Override
    public StepOutcome run(ExecutionContext ctx, FlowState state) {
        if (bus == null || !bus.hasStages(point)) return StepOutcome.CONTINUE;
        FlowState.expose(ctx, state);
        if (conditions != null && !conditions.evaluate(ctx)) return StepOutcome.CONTINUE;
        ctx.remove(INTERRUPT_KEY);
        boolean completed = bus.runPoint(point, ctx);
        ctx.remove(INTERRUPT_KEY);
        return !completed && stopOnInterrupt ? StepOutcome.STOP : StepOutcome.CONTINUE;
    }
}
