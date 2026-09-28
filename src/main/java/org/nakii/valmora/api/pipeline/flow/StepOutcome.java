package org.nakii.valmora.api.pipeline.flow;

/** What a {@link FlowStep} tells its {@link CompiledFlow}: keep going, or stop the flow here. */
public enum StepOutcome {
    CONTINUE,
    STOP
}
