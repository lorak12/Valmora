package org.nakii.valmora.module.script.event;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.impl.VariableEvent;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("scripting")
class VariableEventCompilationTest {

    private VariableEvent factory;
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
        when(ctx.getVariableResolver()).thenReturn(null);

        factory = new VariableEvent();
    }

    private CompiledEvent compile(String action, String path, String value) {
        return factory.compile(new String[]{action, path, value}, EventOptions.DEFAULT);
    }

    @Test
    void testGetName_returnsVariable() {
        assertEquals("variable", factory.getName());
    }

    @Test
    void testSet_playerVar_storesDouble() {
        compile("set", "player.var.gold", "100").execute(ctx);
        assertEquals(100.0, variables.get("gold"));
    }

    @Test
    void testSet_playerVar_storesBoolean_true() {
        compile("set", "player.var.flag", "true").execute(ctx);
        assertEquals(true, variables.get("flag"));
    }

    @Test
    void testSet_playerVar_storesBoolean_false() {
        compile("set", "player.var.flag", "false").execute(ctx);
        assertEquals(false, variables.get("flag"));
    }

    @Test
    void testSet_playerVar_storesString_whenNotNumeric() {
        compile("set", "player.var.name", "hello").execute(ctx);
        assertEquals("hello", variables.get("name"));
    }

    @Test
    void testAdd_playerVar_addsToExistingValue() {
        variables.put("gold", 50.0);
        compile("add", "player.var.gold", "25").execute(ctx);
        assertEquals(75.0, variables.get("gold"));
    }

    @Test
    void testAdd_playerVar_addsToZeroWhenAbsent() {
        compile("add", "player.var.points", "10").execute(ctx);
        assertEquals(10.0, variables.get("points"));
    }

    @Test
    void testRemove_playerVar_removesKey() {
        variables.put("gold", 50.0);
        compile("remove", "player.var.gold", "").execute(ctx);
        assertFalse(variables.containsKey("gold"));
    }

    @Test
    void testSet_nonPlayerVarPath_isNoOp() {
        compile("set", "some.other.path", "99").execute(ctx);
        assertTrue(variables.isEmpty());
    }

    @Test
    void testCompile_tooFewArgs_returnsNoOp() {
        CompiledEvent event = factory.compile(new String[]{"set", "player.var.x"}, EventOptions.DEFAULT);
        assertDoesNotThrow(() -> event.execute(ctx));
        assertTrue(variables.isEmpty());
    }

    @Test
    void testAdd_nonNumericValue_addsZero() {
        variables.put("gold", 50.0);
        compile("add", "player.var.gold", "notanumber").execute(ctx);
        // parseDouble returns 0.0 for non-numeric, so gold stays 50.0
        assertEquals(50.0, variables.get("gold"));
    }
}
