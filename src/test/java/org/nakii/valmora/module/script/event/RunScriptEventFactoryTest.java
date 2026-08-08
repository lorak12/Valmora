package org.nakii.valmora.module.script.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventParser;
import org.nakii.valmora.module.script.event.impl.RunScriptEventFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Needs a real Bukkit scheduler to actually fire the repeating inner event. */
@Tag("mockbukkit")
class RunScriptEventFactoryTest {

    private ServerMock server;
    private RunScriptEventFactory factory;
    private CompiledEvent inner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();

        ScriptModule module = mock(ScriptModule.class);
        Valmora plugin = mock(Valmora.class);
        when(plugin.getServer()).thenReturn(server);
        when(module.getValmora()).thenReturn(plugin);

        EventParser eventParser = mock(EventParser.class);
        inner = mock(CompiledEvent.class);
        when(module.getEventParser()).thenReturn(eventParser);
        when(eventParser.parse("spawn_mob zombie_minion 1")).thenReturn(inner);

        factory = new RunScriptEventFactory(module);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getName_returnsRunScript() {
        assertEquals("run_script", factory.getName());
    }

    @Test
    void tooFewArgs_isNoOp() {
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"20"}, EventOptions.DEFAULT).execute(mock(ExecutionContext.class)));
        verifyNoInteractions(inner);
    }

    @Test
    void nonNumericArgs_isNoOp() {
        assertDoesNotThrow(() -> factory.compile(new String[]{"abc", "5", "spawn_mob", "zombie_minion", "1"},
                EventOptions.DEFAULT).execute(mock(ExecutionContext.class)));
        verifyNoInteractions(inner);
    }

    @Test
    void zeroOrNegativeIntervalOrTimes_isNoOp() {
        assertDoesNotThrow(() -> factory.compile(new String[]{"0", "5", "spawn_mob", "zombie_minion", "1"},
                EventOptions.DEFAULT).execute(mock(ExecutionContext.class)));
        assertDoesNotThrow(() -> factory.compile(new String[]{"20", "0", "spawn_mob", "zombie_minion", "1"},
                EventOptions.DEFAULT).execute(mock(ExecutionContext.class)));
        verifyNoInteractions(inner);
    }

    @Test
    void firesInnerEventExactlyTimesTimes() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(null, null, null);
        factory.compile(new String[]{"1", "3", "spawn_mob", "zombie_minion", "1"}, EventOptions.DEFAULT).execute(ctx);

        server.getScheduler().performTicks(10);

        verify(inner, times(3)).execute(ctx);
    }

    @Test
    void cancelsWhenPlayerCasterGoesOffline() {
        PlayerMock caster = server.addPlayer();
        SimpleExecutionContext ctx = new SimpleExecutionContext(caster, null, null);
        factory.compile(new String[]{"1", "5", "spawn_mob", "zombie_minion", "1"}, EventOptions.DEFAULT).execute(ctx);

        server.getScheduler().performTicks(1);
        caster.disconnect();
        server.getScheduler().performTicks(10);

        // At most 1 firing before disconnect; never reaches the full 5.
        verify(inner, atMost(1)).execute(ctx);
    }
}
