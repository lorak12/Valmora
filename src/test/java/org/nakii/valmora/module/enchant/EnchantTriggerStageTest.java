package org.nakii.valmora.module.enchant;

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
 * Mirrors {@code GuiEventBlockStageTest}'s coverage for the identical dispatch shape
 * {@link EnchantTriggerStage} independently reproduces (see its class doc for why it isn't a
 * shared/reused class) — conditions gate, actions run catching {@link ConditionAbortException},
 * fail-actions on either failure.
 */
class EnchantTriggerStageTest {

    private final ExecutionContext ctx = mock(ExecutionContext.class);

    private CompiledEvent recording(List<String> ran, String name) {
        return c -> ran.add(name);
    }

    private static final CompiledEvent ABORT = c -> { throw new ConditionAbortException(); };

    @Test
    void aConditionEventFailingMidActionsSkipsEveryActionAfterItAndRunsFailActions() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = c -> {
            recording(ran, "before").execute(c);
            ABORT.execute(c);
            recording(ran, "after").execute(c);
        };
        CompiledEvent failActions = recording(ran, "fail");

        EnchantTriggerBlock block = new EnchantTriggerBlock(null, actions, failActions);
        StageResult result = new EnchantTriggerStage("test:block", block).run(ctx);

        assertEquals(List.of("before", "fail"), ran);
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

        EnchantTriggerBlock block = new EnchantTriggerBlock(null, actions, failActions);
        StageResult result = new EnchantTriggerStage("test:block", block).run(ctx);

        assertEquals(List.of("one", "two"), ran);
        assertEquals(StageResult.CONTINUE, result);
    }

    @Test
    void aFailingTopLevelConditionSkipsActionsEntirelyAndGoesStraightToFailActions() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = recording(ran, "actions");
        CompiledEvent failActions = recording(ran, "fail");

        EnchantTriggerBlock block = new EnchantTriggerBlock(c -> false, actions, failActions);
        StageResult result = new EnchantTriggerStage("test:block", block).run(ctx);

        assertEquals(List.of("fail"), ran);
        assertEquals(StageResult.INTERRUPT, result);
    }

    @Test
    void aPassingTopLevelConditionRunsActionsNormally() {
        List<String> ran = new ArrayList<>();
        CompiledEvent actions = recording(ran, "actions");
        CompiledEvent failActions = recording(ran, "fail");

        EnchantTriggerBlock block = new EnchantTriggerBlock(c -> true, actions, failActions);
        StageResult result = new EnchantTriggerStage("test:block", block).run(ctx);

        assertEquals(List.of("actions"), ran);
        assertEquals(StageResult.CONTINUE, result);
    }

    @Test
    void missingFailActionsIsANoOpRatherThanAnException() {
        EnchantTriggerBlock block = new EnchantTriggerBlock(null, ABORT, null);
        assertDoesNotThrow(() -> {
            StageResult result = new EnchantTriggerStage("test:block", block).run(ctx);
            assertEquals(StageResult.INTERRUPT, result);
        });
    }
}
