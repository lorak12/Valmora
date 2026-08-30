package org.nakii.valmora.module.enchant.event;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.enchant.EnchantModule;
import org.nakii.valmora.module.enchant.state.EnchantStateEngine;
import org.nakii.valmora.module.script.event.EventOptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnchantStateEventFactoryTest {

    private final EnchantStateEventFactory factory = new EnchantStateEventFactory();
    private ValmoraAPI api;
    private EnchantModule enchantModule;
    private EnchantStateEngine stateEngine;

    @BeforeEach
    void setUp() {
        api = mock(ValmoraAPI.class);
        ValmoraAPI.setProvider(api);
        enchantModule = mock(EnchantModule.class);
        stateEngine = mock(EnchantStateEngine.class);
        when(api.getEnchantModule()).thenReturn(enchantModule);
        when(enchantModule.getStateEngine()).thenReturn(stateEngine);
    }

    @AfterEach
    void tearDown() {
        ValmoraAPI.setProvider(null);
    }

    private SimpleExecutionContext ctx() {
        return new SimpleExecutionContext(mock(LivingEntity.class), null, null, null);
    }

    @Test
    void getNameReturnsEnchantState() {
        assertEquals("enchant_state", factory.getName());
    }

    @Test
    void tooFewArgsIsNoOp() {
        var ctx = ctx();
        assertDoesNotThrow(() -> factory.compile(new String[]{"increment"}, EventOptions.DEFAULT).execute(ctx));
        verifyNoInteractions(stateEngine);
    }

    @Test
    void incrementDelegatesToTheStateEngineWithZeroAmount() {
        var ctx = ctx();
        factory.compile(new String[]{"increment", "combo"}, EventOptions.DEFAULT).execute(ctx);
        verify(stateEngine).mutate(ctx, "combo", "increment", 0);
    }

    @Test
    void addParsesTheAmountArgument() {
        var ctx = ctx();
        factory.compile(new String[]{"add", "combo", "5"}, EventOptions.DEFAULT).execute(ctx);
        verify(stateEngine).mutate(ctx, "combo", "add", 5);
    }

    @Test
    void resetIgnoresAnyAmountArgument() {
        var ctx = ctx();
        factory.compile(new String[]{"reset", "combo"}, EventOptions.DEFAULT).execute(ctx);
        verify(stateEngine).mutate(ctx, "combo", "reset", 0);
    }

    @Test
    void noStateEngineAvailableIsANoOp() {
        when(enchantModule.getStateEngine()).thenReturn(null);
        var ctx = ctx();
        assertDoesNotThrow(() -> factory.compile(new String[]{"increment", "combo"}, EventOptions.DEFAULT).execute(ctx));
    }
}
