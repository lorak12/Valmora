package org.nakii.valmora.module.enchant;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link EnchantVariableProvider}, {@link EnchantLevelVariableProvider}, and
 *  {@link EnchantCalcVariableProvider} — all read plain context attachments, so no ExecutionContext
 *  subclass or live server is needed, just a real {@link SimpleExecutionContext} with attachments
 *  set directly (matching the values {@code EnchantDispatcher}/{@code DamageCalculator} attach). */
class EnchantVariableProviderTest {

    private final EnchantVariableProvider enchantProvider = new EnchantVariableProvider();
    private final EnchantLevelVariableProvider levelProvider = new EnchantLevelVariableProvider();
    private final EnchantCalcVariableProvider calcProvider = new EnchantCalcVariableProvider();

    private SimpleExecutionContext ctx() {
        LivingEntity caster = mock(LivingEntity.class);
        return new SimpleExecutionContext(caster, null, null, null);
    }

    @Test
    void enchantLevelReadsTheAttachedLevel() {
        var ctx = ctx();
        ctx.set("enchant:level", 5);
        assertEquals(5, enchantProvider.resolve(new String[]{"level"}, ctx));
    }

    @Test
    void bareLevelProviderIsEquivalentToEnchantLevel() {
        var ctx = ctx();
        ctx.set("enchant:level", 3);
        assertEquals(3, levelProvider.resolve(new String[]{}, ctx));
    }

    @Test
    void enchantLevelDefaultsToZeroWhenUnset() {
        assertEquals(0, enchantProvider.resolve(new String[]{"level"}, ctx()));
    }

    @Test
    void enchantStateReadsThePersistentValueOnTheAttachedInstance() {
        var ctx = ctx();
        var instance = new EnchantStateStore.EnchantInstance("champion", 3, Map.of("kills", 1542));
        ctx.set("enchant:instance", instance);
        assertEquals(1542, enchantProvider.resolve(new String[]{"state", "kills"}, ctx));
    }

    @Test
    void enchantStateDefaultsToZeroForAnUnsetKey() {
        var ctx = ctx();
        var instance = new EnchantStateStore.EnchantInstance("champion", 3, Map.of());
        ctx.set("enchant:instance", instance);
        assertEquals(0, enchantProvider.resolve(new String[]{"state", "kills"}, ctx));
    }

    @Test
    void enchantStateWithNoInstanceAttachedIsZero() {
        assertEquals(0, enchantProvider.resolve(new String[]{"state", "kills"}, ctx()));
    }

    @Test
    void calcReadsThePreEvaluatedFormulaValue() {
        var ctx = ctx();
        ctx.set("calc:bonus", 42.0);
        assertEquals(42.0, calcProvider.resolve(new String[]{"bonus"}, ctx));
    }

    @Test
    void calcIsNullForAnUnattachedName() {
        assertNull(calcProvider.resolve(new String[]{"unknown"}, ctx()));
    }
}
