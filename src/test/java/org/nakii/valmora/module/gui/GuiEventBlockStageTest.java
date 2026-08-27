package org.nakii.valmora.module.gui;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.ConditionAbortException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * docs/V1_RELEASE_CHECKLIST.md §1 "Script DSL condition/branch coverage" — a `condition` event
 * failing inside a GUI event block's {@code actions} must short-circuit every action after it and
 * run {@code fail-actions} instead (CLAUDE.md §8.2/§10.2), never both and never neither. This is
 * the single place that contract is implemented (see the class Javadoc on
 * {@link GuiEventBlockStage} for why the older three-copies-of-this-logic version was consolidated
 * here) but it had no test of its own.
 */
class GuiEventBlockStageTest {

    private final ExecutionContext ctx = mock(ExecutionContext.class);

    /** A CompiledEvent that records its own name into {@code ran} when executed. */
    private CompiledEvent recording(List<String> ran, String name) {
        return c -> ran.add(name);
    }

    /** A CompiledEvent standing in for a failed `condition ...` event: aborts, recording nothing. */
    private static final CompiledEvent ABORT = c -> { throw new ConditionAbortException(); };

    @Test
    void aConditionEventFailingMidActionsSkipsEveryActionAfterItAndRunsFailActions() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = c -> {
            recording(ran, "before").execute(c);
            ABORT.execute(c);
            recording(ran, "after").execute(c); // must never run
        };
        CompiledEvent failActions = recording(ran, "fail");

        GuiEventBlock block = new GuiEventBlock(null, actions, failActions);
        StageResult result = new GuiEventBlockStage("test:block", block).run(ctx);

        assertEquals(List.of("before", "fail"), ran, "must run everything before the abort, then fail-actions, and nothing after the abort");
        assertEquals(StageResult.INTERRUPT, result);
    }

    @Test
    void aPassingActionsListRunsInFullAndNeverTouchesFailActions() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = c -> {
            recording(ran, "one").execute(c);
            recording(ran, "two").execute(c);
        };
        CompiledEvent failActions = recording(ran, "fail");

        GuiEventBlock block = new GuiEventBlock(null, actions, failActions);
        StageResult result = new GuiEventBlockStage("test:block", block).run(ctx);

        assertEquals(List.of("one", "two"), ran);
        assertEquals(StageResult.CONTINUE, result);
    }

    @Test
    void aFailingTopLevelConditionSkipsActionsEntirelyAndGoesStraightToFailActions() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = recording(ran, "actions"); // must never run
        CompiledEvent failActions = recording(ran, "fail");

        GuiEventBlock block = new GuiEventBlock(c -> false, actions, failActions);
        StageResult result = new GuiEventBlockStage("test:block", block).run(ctx);

        assertEquals(List.of("fail"), ran);
        assertEquals(StageResult.INTERRUPT, result);
    }

    @Test
    void aPassingTopLevelConditionRunsActionsNormally() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = recording(ran, "actions");
        CompiledEvent failActions = recording(ran, "fail");

        GuiEventBlock block = new GuiEventBlock(c -> true, actions, failActions);
        StageResult result = new GuiEventBlockStage("test:block", block).run(ctx);

        assertEquals(List.of("actions"), ran);
        assertEquals(StageResult.CONTINUE, result);
    }

    @Test
    void missingFailActionsIsANoOpRatherThanAnException() {
        CompiledEvent actions = ABORT;
        GuiEventBlock block = new GuiEventBlock(null, actions, null);

        assertDoesNotThrow(() -> {
            StageResult result = new GuiEventBlockStage("test:block", block).run(ctx);
            assertEquals(StageResult.INTERRUPT, result);
        });
    }

    @Test
    void aConditionAbortInsideFailActionsItselfIsSwallowedRatherThanPropagating() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = ABORT;
        CompiledEvent failActions = c -> {
            recording(ran, "fail-before").execute(c);
            ABORT.execute(c);
            recording(ran, "fail-after").execute(c); // must never run either
        };

        GuiEventBlock block = new GuiEventBlock(null, actions, failActions);
        StageResult result = assertDoesNotThrow(() -> new GuiEventBlockStage("test:block", block).run(ctx));

        assertEquals(List.of("fail-before"), ran);
        assertEquals(StageResult.INTERRUPT, result);
    }

    @Test
    void nullActionsWithPassingConditionIsANoOpThatContinues() {
        GuiEventBlock block = new GuiEventBlock(c -> true, null, null);
        assertEquals(StageResult.CONTINUE, new GuiEventBlockStage("test:block", block).run(ctx));
    }
}
