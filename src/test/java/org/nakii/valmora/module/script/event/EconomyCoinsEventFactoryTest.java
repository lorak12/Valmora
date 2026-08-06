package org.nakii.valmora.module.script.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.script.event.impl.EconomyCoinsEventFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomyCoinsEventFactoryTest {

    private EconomyService economy;
    private Player caster;
    private SimpleExecutionContext ctx;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        economy = mock(EconomyService.class);
        when(api.getEconomy()).thenReturn(economy);
        ValmoraAPI.setProvider(api);

        caster = mock(Player.class);
        Location loc = mock(Location.class);
        when(caster.getLocation()).thenReturn(loc);
        ctx = new SimpleExecutionContext(caster, loc, null);
    }

    @Test
    void giveCoins_getName() {
        assertEquals("give_coins", new EconomyCoinsEventFactory(true).getName());
    }

    @Test
    void takeCoins_getName() {
        assertEquals("take_coins", new EconomyCoinsEventFactory(false).getName());
    }

    @Test
    void giveCoins_defaultSelector_addsToCaster() {
        new EconomyCoinsEventFactory(true).compile(new String[]{"100"}, EventOptions.DEFAULT).execute(ctx);
        verify(economy).addCoins(caster, 100.0);
    }

    @Test
    void takeCoins_defaultSelector_removesFromCaster() {
        new EconomyCoinsEventFactory(false).compile(new String[]{"50"}, EventOptions.DEFAULT).execute(ctx);
        verify(economy).removeCoins(caster, 50.0);
    }

    @Test
    void zeroOrNegativeAmount_isNoOp() {
        new EconomyCoinsEventFactory(true).compile(new String[]{"0"}, EventOptions.DEFAULT).execute(ctx);
        new EconomyCoinsEventFactory(true).compile(new String[]{"-10"}, EventOptions.DEFAULT).execute(ctx);
        verifyNoInteractions(economy);
    }

    @Test
    void missingArgs_isNoOp() {
        assertDoesNotThrow(() ->
                new EconomyCoinsEventFactory(true).compile(new String[0], EventOptions.DEFAULT).execute(ctx));
        verifyNoInteractions(economy);
    }
}
