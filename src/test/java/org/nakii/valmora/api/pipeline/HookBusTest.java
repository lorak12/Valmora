package org.nakii.valmora.api.pipeline;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the generic {@link HookBus} primitive introduced by
 * docs/COMBAT_PIPELINE_ANALYSIS.md — the shared dispatch bus combat (and future domains) register
 * stages on, instead of each hand-rolling its own trigger/condition/action loop.
 */
public class HookBusTest {

    private HookBus bus;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("HookBusTest"));
        bus = new HookBus(plugin);
    }

    private ExecutionContext ctx() {
        return new SimpleExecutionContext(null, null, null);
    }

    @Test
    void emptyBusHasNoStagesAndRunPointIsANoOp() {
        assertFalse(bus.hasStages("combat:pre_damage"));
        assertFalse(bus.hasAnyStages("combat:pre_damage", "combat:on_death"));
        assertTrue(bus.runPoint("combat:pre_damage", ctx()));
    }

    @Test
    void registeredJavaHookRunsAndCanContinue() {
        AtomicInteger calls = new AtomicInteger();
        bus.registerHook("combat:pre_damage", "counter", c -> {
            calls.incrementAndGet();
            return StageResult.CONTINUE;
        });

        assertTrue(bus.hasStages("combat:pre_damage"));
        assertTrue(bus.runPoint("combat:pre_damage", ctx()));
        assertEquals(1, calls.get());
    }

    @Test
    void interruptStopsRemainingStagesAndReturnsFalse() {
        List<String> ran = new java.util.ArrayList<>();
        bus.registerHook("combat:pre_damage", "first", c -> {
            ran.add("first");
            return StageResult.INTERRUPT;
        });
        bus.registerHook("combat:pre_damage", "second", c -> {
            ran.add("second");
            return StageResult.CONTINUE;
        });

        boolean result = bus.runPoint("combat:pre_damage", ctx());

        assertFalse(result);
        assertEquals(List.of("first"), ran);
    }

    @Test
    void javaHooksRunBeforeYamlStagesAtTheSamePoint() {
        List<String> ran = new java.util.ArrayList<>();
        bus.registerYamlStage("combat:pre_damage", new RecordingStage("yaml", ran));
        bus.registerHook("combat:pre_damage", "java", c -> {
            ran.add("java");
            return StageResult.CONTINUE;
        });

        bus.runPoint("combat:pre_damage", ctx());

        assertEquals(List.of("java", "yaml"), ran);
    }

    @Test
    void clearYamlStagesOnlyRemovesMatchingPrefixAndLeavesJavaHooksAlone() {
        bus.registerYamlStage("combat:pre_damage", new RecordingStage("yaml", new java.util.ArrayList<>()));
        bus.registerYamlStage("fishing:pre_catch", new RecordingStage("yaml-fish", new java.util.ArrayList<>()));
        bus.registerHook("combat:pre_damage", "java", c -> StageResult.CONTINUE);

        bus.clearYamlStages("combat:");

        assertTrue(bus.hasStages("combat:pre_damage")); // yaml stage gone, but the java hook remains
        assertTrue(bus.hasStages("fishing:pre_catch")); // untouched — different prefix
    }

    @Test
    void unregisterHookRemovesOnlyThatId() {
        bus.registerHook("combat:pre_damage", "a", c -> StageResult.CONTINUE);
        bus.registerHook("combat:pre_damage", "b", c -> StageResult.CONTINUE);

        bus.unregisterHook("combat:pre_damage", "a");

        List<String> ran = new java.util.ArrayList<>();
        bus.registerYamlStage("combat:pre_damage", new RecordingStage("marker", ran));
        bus.runPoint("combat:pre_damage", ctx());
        assertEquals(List.of("marker"), ran); // "a" no longer fires; "b" still would but doesn't record
    }

    @Test
    void throwingStageIsLoggedAndSkippedRatherThanBreakingThePipeline() {
        List<String> ran = new java.util.ArrayList<>();
        bus.registerHook("combat:pre_damage", "boom", c -> {
            throw new RuntimeException("simulated broken addon hook");
        });
        bus.registerHook("combat:pre_damage", "after", c -> {
            ran.add("after");
            return StageResult.CONTINUE;
        });

        boolean result = bus.runPoint("combat:pre_damage", ctx());

        assertTrue(result);
        assertEquals(List.of("after"), ran);
    }

    private record RecordingStage(String id, List<String> sink) implements PipelineStage {
        @Override
        public String getId() {
            return id;
        }

        @Override
        public StageResult run(ExecutionContext context) {
            sink.add(id);
            return StageResult.CONTINUE;
        }
    }
}
