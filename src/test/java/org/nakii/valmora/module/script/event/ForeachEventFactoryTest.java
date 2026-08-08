package org.nakii.valmora.module.script.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventParser;
import org.nakii.valmora.module.script.event.impl.ForeachEventFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Needs a real Bukkit server for {@code Bukkit.getOnlinePlayers()}/{@code Location#getNearbyPlayers}
 * — see MockBukkit-tagged sibling tests (ModuleManagerReloadSafetyTest, ProfilePersistenceMockTest).
 */
@Tag("mockbukkit")
class ForeachEventFactoryTest {

    private ServerMock server;
    private ForeachEventFactory factory;
    private ScriptModule module;
    private CompiledEvent inner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        module = mock(ScriptModule.class);
        EventParser eventParser = mock(EventParser.class);
        inner = mock(CompiledEvent.class);
        when(module.getEventParser()).thenReturn(eventParser);
        when(eventParser.parse("give STONE:1")).thenReturn(inner);
        factory = new ForeachEventFactory(module);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getName_returnsForeach() {
        assertEquals("foreach", factory.getName());
    }

    @Test
    void tooFewArgs_isNoOp() {
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"@all"}, EventOptions.DEFAULT).execute(mock(ExecutionContext.class)));
        verifyNoInteractions(inner);
    }

    @Test
    void atAll_executesInnerEventForEveryOnlinePlayer() {
        PlayerMock p1 = server.addPlayer();
        PlayerMock p2 = server.addPlayer();

        CompiledEvent event = factory.compile(new String[]{"@all", "give", "STONE:1"}, EventOptions.DEFAULT);
        SimpleExecutionContext outer = new SimpleExecutionContext(null, null, null);
        event.execute(outer);

        verify(inner, times(2)).execute(any());
    }

    @Test
    void atNearby_executesOnlyForPlayersWithinRadius() {
        PlayerMock near = server.addPlayer();
        near.teleport(new org.bukkit.Location(near.getWorld(), 0, 64, 0));
        PlayerMock far = server.addPlayer();
        far.teleport(new org.bukkit.Location(far.getWorld(), 1000, 64, 1000));

        CompiledEvent event = factory.compile(new String[]{"@nearby:10", "give", "STONE:1"}, EventOptions.DEFAULT);
        SimpleExecutionContext outer = new SimpleExecutionContext(null, near.getLocation(), null);
        event.execute(outer);

        verify(inner, times(1)).execute(any());
    }

    @Test
    void atNearby_malformedRadius_isNoOp() {
        CompiledEvent event = factory.compile(new String[]{"@nearby:abc", "give", "STONE:1"}, EventOptions.DEFAULT);
        assertDoesNotThrow(() -> event.execute(new SimpleExecutionContext(null, null, null)));
        verifyNoInteractions(inner);
    }

    @Test
    void unknownSelector_isNoOp() {
        CompiledEvent event = factory.compile(new String[]{"@bogus", "give", "STONE:1"}, EventOptions.DEFAULT);
        assertDoesNotThrow(() -> event.execute(new SimpleExecutionContext(null, null, null)));
        verifyNoInteractions(inner);
    }

    @Test
    void innerContext_stashesOriginalCasterAttachment() {
        org.bukkit.entity.LivingEntity originalCaster = mock(org.bukkit.entity.LivingEntity.class);
        PlayerMock p1 = server.addPlayer();

        CompiledEvent event = factory.compile(new String[]{"@all", "give", "STONE:1"}, EventOptions.DEFAULT);
        SimpleExecutionContext outer = new SimpleExecutionContext(originalCaster, null, null);
        event.execute(outer);

        org.mockito.ArgumentCaptor<ExecutionContext> captor = org.mockito.ArgumentCaptor.forClass(ExecutionContext.class);
        verify(inner).execute(captor.capture());
        assertEquals(originalCaster, captor.getValue().get("foreach:original_caster"));
        // The target player, not the outer caster, becomes the inner context's caster.
        assertEquals(p1, captor.getValue().getCaster());
    }
}
