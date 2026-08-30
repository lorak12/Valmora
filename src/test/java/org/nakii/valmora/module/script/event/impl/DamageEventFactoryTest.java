package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.script.event.EventOptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Full damage-application coverage lives with {@code DamageCalculator}/{@code DamageResult}
 *  (which this event just delegates to, identically to the {@code DamageMechanic} ability-mechanic
 *  equivalent) — this only covers this event's own arg parsing/no-op behavior. */
class DamageEventFactoryTest {

    private final DamageEventFactory factory = new DamageEventFactory();

    @Test
    void getNameReturnsDamage() {
        assertEquals("damage", factory.getName());
    }

    @Test
    void tooFewArgsIsNoOp() {
        var ctx = new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
        assertDoesNotThrow(() -> factory.compile(new String[]{"@target"}, EventOptions.DEFAULT).execute(ctx));
    }

    @Test
    void zeroOrNegativeAmountIsNoOpEvenWithATargetPresent() {
        LivingEntity caster = mock(LivingEntity.class);
        LivingEntity target = mock(LivingEntity.class);
        var ctx = new SimpleExecutionContext(caster, target, null, null);
        // Would throw reaching DamageCalculator (ValmoraAPI unset) if the zero-amount guard didn't
        // short-circuit first.
        assertDoesNotThrow(() -> factory.compile(new String[]{"@target", "0"}, EventOptions.DEFAULT).execute(ctx));
    }

    @Test
    void noTargetIsANoOpEvenWithAPositiveAmount() {
        LivingEntity caster = mock(LivingEntity.class);
        var ctx = new SimpleExecutionContext(caster, null, null, null);
        assertDoesNotThrow(() -> factory.compile(new String[]{"@target", "10"}, EventOptions.DEFAULT).execute(ctx));
    }
}
