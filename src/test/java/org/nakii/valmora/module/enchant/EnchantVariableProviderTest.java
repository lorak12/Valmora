package org.nakii.valmora.module.enchant;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.enchant.state.EnchantStateEngine;
import org.nakii.valmora.module.enchant.state.PersistentStateDefinition;
import org.nakii.valmora.module.enchant.state.TransientStateTracker;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers {@link EnchantVariableProvider}, {@link EnchantLevelVariableProvider}, and
 *  {@link EnchantCalcVariableProvider} — all read plain context attachments, so no ExecutionContext
 *  subclass or live server is needed, just a real {@link SimpleExecutionContext} with attachments
 *  set directly (matching the values {@code EnchantDispatcher}/{@code DamageCalculator} attach). The
 *  {@code state} case now delegates to {@link EnchantStateEngine} (Phase 3), so the "state" tests
 *  set up a real registry/engine behind a mocked {@link ValmoraAPI}. */
class EnchantVariableProviderTest {

    private final EnchantVariableProvider enchantProvider = new EnchantVariableProvider();
    private final EnchantLevelVariableProvider levelProvider = new EnchantLevelVariableProvider();
    private final EnchantCalcVariableProvider calcProvider = new EnchantCalcVariableProvider();
    private EnchantmentRegistry registry;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        ValmoraAPI.setProvider(api);
        EnchantModule enchantModule = mock(EnchantModule.class);
        registry = new EnchantmentRegistry();
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(enchantModule.getRegistry()).thenReturn(registry);
        when(enchantModule.getStateEngine()).thenReturn(new EnchantStateEngine(new TransientStateTracker()));
    }

    @AfterEach
    void tearDown() {
        ValmoraAPI.setProvider(null);
    }

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
        registry.register("champion", EnchantmentDefinition.builder("champion")
                .persistentState("kills", new PersistentStateDefinition(0))
                .build());

        var ctx = ctx();
        var instance = new EnchantStateStore.EnchantInstance("champion", 3, Map.of("kills", 1542));
        ctx.set("enchant:id", "champion");
        ctx.set("enchant:instance", instance);
        assertEquals(1542, enchantProvider.resolve(new String[]{"state", "kills"}, ctx));
    }

    @Test
    void enchantStateDefaultsToZeroForAnUnsetKey() {
        registry.register("champion", EnchantmentDefinition.builder("champion")
                .persistentState("kills", new PersistentStateDefinition(0))
                .build());

        var ctx = ctx();
        var instance = new EnchantStateStore.EnchantInstance("champion", 3, Map.of());
        ctx.set("enchant:id", "champion");
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
