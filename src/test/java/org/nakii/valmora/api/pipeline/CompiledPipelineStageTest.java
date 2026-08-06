package org.nakii.valmora.api.pipeline;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.script.event.ConditionAbortException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompiledPipelineStageTest {

    private ExecutionContext ctx() {
        return new SimpleExecutionContext(null, null, null);
    }

    @Test
    void failingCondition_runsOnFail_andContinues() {
        List<String> ran = new ArrayList<>();
        var stage = new CompiledPipelineStage("s", c -> false,
                c -> ran.add("pass"), c -> ran.add("fail"));

        assertEquals(StageResult.CONTINUE, stage.run(ctx()));
        assertEquals(List.of("fail"), ran);
    }

    @Test
    void passingCondition_runsOnPass_andContinuesByDefault() {
        List<String> ran = new ArrayList<>();
        var stage = new CompiledPipelineStage("s", c -> true,
                c -> ran.add("pass"), c -> ran.add("fail"));

        assertEquals(StageResult.CONTINUE, stage.run(ctx()));
        assertEquals(List.of("pass"), ran);
    }

    @Test
    void interruptEventDuringOnPass_reportsInterrupt() {
        var stage = new CompiledPipelineStage("s", c -> true,
                c -> c.set("pipeline:interrupted", Boolean.TRUE), c -> {});

        assertEquals(StageResult.INTERRUPT, stage.run(ctx()));
    }

    /**
     * A mid-list `condition` event throwing {@link ConditionAbortException} inside onPass falls
     * back to onFail — same as GUI event blocks and abilities — but (unlike the GUI adapter) still
     * reports CONTINUE, since a pipeline point is a list of independent stages, not a single gate.
     */
    @Test
    void conditionAbortDuringOnPass_fallsBackToOnFail_andStillContinues() {
        List<String> ran = new ArrayList<>();
        CompiledEventThatAborts onPass = new CompiledEventThatAborts(ran);
        var stage = new CompiledPipelineStage("s", c -> true, onPass, c -> ran.add("fail"));

        assertEquals(StageResult.CONTINUE, stage.run(ctx()));
        assertEquals(List.of("started", "fail"), ran);
    }

    private record CompiledEventThatAborts(List<String> ran) implements org.nakii.valmora.api.scripting.CompiledEvent {
        @Override
        public void execute(ExecutionContext context) {
            ran.add("started");
            throw new ConditionAbortException();
        }
    }
}
