package org.nakii.valmora.module.script.event;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.impl.CounterEventFactory;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CounterEventFactoryTest {

    private CounterEventFactory factory;
    private Map<String, Object> variables;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        ValmoraAPI api = mock(ValmoraAPI.class);
        PlayerManager playerManager = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        ValmoraPlayer vPlayer = new ValmoraPlayer(uuid);
        ValmoraProfile profile = new ValmoraProfile("Test");
        vPlayer.addProfile(profile);
        variables = profile.getVariables();
        when(playerManager.getSession(uuid)).thenReturn(vPlayer);

        ctx = mock(ExecutionContext.class);
        when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));

        factory = new CounterEventFactory();
    }

    private CompiledEvent compile(String... args) {
        return factory.compile(args, EventOptions.DEFAULT);
    }

    @Test
    void getName_returnsCounter() {
        assertEquals("counter", factory.getName());
    }

    @Test
    void increment_withNoAmount_addsOne() {
        compile("increment", "player.var.combo").execute(ctx);
        assertEquals(1.0, variables.get("combo"));
        compile("increment", "player.var.combo").execute(ctx);
        assertEquals(2.0, variables.get("combo"));
    }

    @Test
    void increment_withAmount_addsThatAmount() {
        compile("increment", "player.var.combo", "5").execute(ctx);
        assertEquals(5.0, variables.get("combo"));
    }

    @Test
    void decrement_subtractsAmount() {
        variables.put("combo", 10.0);
        compile("decrement", "player.var.combo", "3").execute(ctx);
        assertEquals(7.0, variables.get("combo"));
    }

    @Test
    void add_addsArbitraryAmount() {
        variables.put("gold", 50.0);
        compile("add", "player.var.gold", "25").execute(ctx);
        assertEquals(75.0, variables.get("gold"));
    }

    @Test
    void reset_setsToZero() {
        variables.put("combo", 42.0);
        compile("reset", "player.var.combo").execute(ctx);
        assertEquals(0.0, variables.get("combo"));
    }

    @Test
    void nonPlayerVarPath_isNoOp() {
        compile("increment", "prop.foo").execute(ctx);
        assertTrue(variables.isEmpty());
    }

    @Test
    void tooFewArgs_isNoOp() {
        CompiledEvent event = factory.compile(new String[]{"increment"}, EventOptions.DEFAULT);
        assertDoesNotThrow(() -> event.execute(ctx));
        assertTrue(variables.isEmpty());
    }
}
