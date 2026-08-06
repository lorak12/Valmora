package org.nakii.valmora.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.combat.DamageFormulaRegistry;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Micro-benchmark for the AST pre-compilation work done across Phases 2-4 of the refactor (see
 * docs/REFACTOR/PROGRESS.md — Testing &amp; Verification Protocol #2 / "Not done in Phase 5" /
 * CLAUDE.md's mandatory-rule #4 "No Hot-Path String Interpretation").
 *
 * <p>Rather than comparing the compiled formula path against unrelated raw Java arithmetic (which
 * would trivially "win" and prove nothing useful — a tree-walking interpreter is never going to
 * match a JIT-inlined double multiply), this compares the two ways the codebase could plausibly
 * evaluate {@code damage_formula.yml}'s formulas: the pre-compiled {@link Expression} AST cached
 * once by {@link DamageFormulaRegistry} at load time (what the code actually does), versus
 * re-parsing the same raw formula string on every single call (the exact anti-pattern CLAUDE.md's
 * rule #4 and Task 20 forbid, and what {@code OpenGuiEventFactory}/{@code AbilityExecutor} used to
 * do before their Phase 5 fixes). The pre-compiled path must be meaningfully faster under load.
 */
public class ExternalizedSystemTest {

    private static final String FORMULA = "1 + $dmg.strength$ / 100";
    // Deliberately variable-free and more token-heavy than FORMULA: isolates raw parse cost from
    // evaluate() cost. A variable-bearing formula's evaluate() step (going through
    // ValmoraAPI -> ScriptModule -> VariableResolver) dominates the timing either way, which
    // drowns out the parse-time difference the comparison test below is trying to measure.
    private static final String ARITHMETIC_FORMULA =
            "floor(min(1 + 2 * 3 - 4 / 2, 100)) + max(5, 10 - 3) * 2 - abs(0 - 8) + round(2.6)";
    private static final int ITERATIONS = 50_000;

    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        ScriptModule scriptModule = mock(ScriptModule.class);
        VariableResolver variableResolver = mock(VariableResolver.class);
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(variableResolver);
        when(variableResolver.resolve(eq("$dmg.strength$"), any())).thenReturn(50.0);

        context = new SimpleExecutionContext(null, null, null);
    }

    @Test
    void compiledFormulaEvaluationIsSignificantlyFasterThanReparsingPerCall() {
        Expression compiled = new ExpressionParser().parse(ARITHMETIC_FORMULA);

        // Warm up the JIT for both paths equally before timing, to avoid measuring interpreter
        // startup/cold-code overhead rather than the actual steady-state cost difference.
        for (int i = 0; i < 5_000; i++) {
            compiled.evaluate(context);
            new ExpressionParser().parse(ARITHMETIC_FORMULA).evaluate(context);
        }

        long compiledNanos = timeNanos(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                compiled.evaluate(context);
            }
        });

        long reparsedNanos = timeNanos(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                // The anti-pattern: parse the raw string fresh on every single call.
                new ExpressionParser().parse(ARITHMETIC_FORMULA).evaluate(context);
            }
        });

        assertTrue(compiledNanos > 0 && reparsedNanos > 0, "both timings must be measurable");
        assertTrue(compiledNanos < reparsedNanos,
                "pre-compiled evaluation (" + compiledNanos + "ns) must beat re-parsing per call (" + reparsedNanos + "ns)");

        // Conservative threshold (not "must be N times faster exactly") to avoid CI flakiness —
        // this only needs to catch a real regression (e.g. someone reverting to raw re-parsing),
        // not enforce a precise performance target.
        double speedup = (double) reparsedNanos / compiledNanos;
        assertTrue(speedup > 1.5,
                "expected pre-compilation to give a meaningful speedup, got " + speedup + "x "
                        + "(compiled=" + compiledNanos + "ns, reparsed=" + reparsedNanos + "ns)");
    }

    @Test
    void damageFormulaRegistryEvaluationStaysFastUnderLoad() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ExternalizedSystemTest"));

        DamageFormulaRegistry registry = new DamageFormulaRegistry(plugin, new ExpressionParser());
        registry.load();

        // Warm-up.
        for (int i = 0; i < 5_000; i++) {
            registry.evaluate(DamageFormulaRegistry.DAMAGE_MULTIPLIER, context, -1);
        }

        long nanos = timeNanos(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                registry.evaluate(DamageFormulaRegistry.DAMAGE_MULTIPLIER, context, -1);
            }
        });

        double nanosPerCall = (double) nanos / ITERATIONS;
        // Generous absolute ceiling — this is a sanity guard against a catastrophic regression
        // (e.g. accidentally reintroducing per-call string parsing), not a tight perf budget.
        assertTrue(nanosPerCall < 50_000, // 50 microseconds/call
                "damage formula evaluation should stay well under 50µs/call on the combat hot path, "
                        + "measured " + nanosPerCall + "ns/call");
    }

    private long timeNanos(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return System.nanoTime() - start;
    }
}
