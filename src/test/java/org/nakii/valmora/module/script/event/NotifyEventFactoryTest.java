package org.nakii.valmora.module.script.event;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.event.impl.NotifyEventFactory;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotifyEventFactoryTest {

    private final NotifyEventFactory factory = new NotifyEventFactory();
    private Player player;
    private VariableResolver resolver;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        resolver = mock(VariableResolver.class);
        ctx = mock(ExecutionContext.class);
        when(ctx.getPlayerCaster()).thenReturn(Optional.of(player));
        when(ctx.getVariableResolver()).thenReturn(resolver);
    }

    @Test
    void getName_returnsNotify() {
        assertEquals("notify", factory.getName());
    }

    @Test
    void noArgs_isNoOp() {
        CompiledEvent event = factory.compile(new String[0], EventOptions.DEFAULT);
        assertDoesNotThrow(() -> event.execute(ctx));
        verifyNoInteractions(player);
    }

    @Test
    void sendsResolvedMessageToCaster() {
        when(resolver.resolveTemplate("Hello $player.name$", ctx)).thenReturn("Hello Steve");
        CompiledEvent event = factory.compile(new String[]{"Hello", "$player.name$"}, EventOptions.DEFAULT);

        event.execute(ctx);

        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void noPlayerCaster_isNoOp() {
        when(ctx.getPlayerCaster()).thenReturn(Optional.empty());
        CompiledEvent event = factory.compile(new String[]{"Hello"}, EventOptions.DEFAULT);
        assertDoesNotThrow(() -> event.execute(ctx));
    }
}
