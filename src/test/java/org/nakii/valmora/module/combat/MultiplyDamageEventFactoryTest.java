package org.nakii.valmora.module.combat;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the {@code multiply_damage} DSL event — the resolution to the pipeline/enchant ordering
 * question raised in docs/COMBAT_PIPELINE_ANALYSIS.md §5 (see the exact application point
 * documented on {@link DamageCalculator}'s 5-arg {@code calculateDamage} overload).
 */
class MultiplyDamageEventFactoryTest {

    private final MultiplyDamageEventFactory factory = new MultiplyDamageEventFactory();

    private ExecutionContext ctx() {
        return new SimpleExecutionContext(null, null, null);
    }

    @Test
    void getName_returnsMultiplyDamage() {
        assertEquals("multiply_damage", factory.getName());
    }

    @Test
    void firstCall_setsMultiplierFromOne() {
        ExecutionContext ctx = ctx();
        compile("1.5").execute(ctx);
        assertEquals(1.5, (double) ctx.get("dmg:pipeline_multiplier", 1.0));
    }

    @Test
    void repeatedCallsCompoundMultiplicatively() {
        ExecutionContext ctx = ctx();
        compile("1.5").execute(ctx);
        compile("1.5").execute(ctx);
        assertEquals(2.25, (double) ctx.get("dmg:pipeline_multiplier", 1.0), 1e-9);
    }

    @Test
    void nonNumericFactor_fallsBackToNoOpMultiplier() {
        ExecutionContext ctx = ctx();
        compile("not-a-number").execute(ctx);
        assertEquals(1.0, (double) ctx.get("dmg:pipeline_multiplier", 1.0));
    }

    @Test
    void missingArgs_isNoOp() {
        ExecutionContext ctx = ctx();
        CompiledEvent event = factory.compile(new String[0], EventOptions.DEFAULT);
        assertEquals(1.0, (double) ctx.get("dmg:pipeline_multiplier", 1.0));
        event.execute(ctx);
        assertEquals(1.0, (double) ctx.get("dmg:pipeline_multiplier", 1.0));
    }

    private CompiledEvent compile(String factorArg) {
        return factory.compile(new String[]{factorArg}, EventOptions.DEFAULT);
    }
}
