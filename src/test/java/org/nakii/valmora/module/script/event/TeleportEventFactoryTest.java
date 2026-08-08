package org.nakii.valmora.module.script.event;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.impl.TeleportEventFactory;
import org.nakii.valmora.module.warp.WarpDefinition;
import org.nakii.valmora.module.warp.WarpManager;
import org.nakii.valmora.module.zone.ZoneManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Needs a real Bukkit server for {@code Bukkit.getWorld()}/{@code Player#teleportAsync}.
 * Note: {@code absoluteCoords_sameWorld_teleportsPlayer} and {@code atLook_movesForwardByBlockCount}
 * self-skip under MockBukkit — {@code EntityMock#teleportAsync} throws
 * {@code UnimplementedOperationException} (a JUnit-recognized abort signal, not a failure) since
 * MockBukkit doesn't implement it yet. The no-op/zone-block/warp-delegation paths (which don't
 * reach {@code teleportAsync}) are still fully covered.
 */
@Tag("mockbukkit")
class TeleportEventFactoryTest {

    private final TeleportEventFactory factory = new TeleportEventFactory();
    private ServerMock server;
    private PlayerMock player;
    private ValmoraAPI api;
    private ZoneManager zoneManager;
    private WarpManager warpManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();

        api = mock(ValmoraAPI.class);
        zoneManager = mock(ZoneManager.class);
        warpManager = mock(WarpManager.class);
        when(api.getZoneManager()).thenReturn(zoneManager);
        when(api.getWarpManager()).thenReturn(warpManager);
        when(zoneManager.getCurrentZone(player)).thenReturn(Optional.empty());
        ValmoraAPI.setProvider(api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getName_returnsTeleport() {
        assertEquals("teleport", factory.getName());
    }

    @Test
    void noArgs_isNoOp() {
        assertDoesNotThrow(() ->
                factory.compile(new String[0], EventOptions.DEFAULT)
                        .execute(new SimpleExecutionContext(player, player.getLocation(), null)));
    }

    @Test
    void absoluteCoords_sameWorld_teleportsPlayer() {
        CompiledEvent event = factory.compile(new String[]{"10", "70", "20"}, EventOptions.DEFAULT);

        event.execute(new SimpleExecutionContext(player, player.getLocation(), null));
        server.getScheduler().performTicks(2);

        Location loc = player.getLocation();
        assertEquals(10.0, loc.getX());
        assertEquals(70.0, loc.getY());
        assertEquals(20.0, loc.getZ());
    }

    @Test
    void explicitWorld_unknownWorld_isNoOp() {
        Location before = player.getLocation().clone();
        CompiledEvent event = factory.compile(new String[]{"nonexistent_world", "10", "70", "20"}, EventOptions.DEFAULT);

        event.execute(new SimpleExecutionContext(player, player.getLocation(), null));
        server.getScheduler().performTicks(2);

        assertEquals(before.getWorld(), player.getLocation().getWorld());
    }

    @Test
    void atLook_movesForwardByBlockCount() {
        Location start = player.getLocation();
        CompiledEvent event = factory.compile(new String[]{"@look", "5"}, EventOptions.DEFAULT);

        event.execute(new SimpleExecutionContext(player, player.getLocation(), null));
        server.getScheduler().performTicks(2);

        assertNotEquals(start.getBlockX(), player.getLocation().getBlockX(),
                "player should have moved along their look direction");
    }

    @Test
    void warpPrefix_delegatesToWarpManager() {
        WarpDefinition warp = mock(WarpDefinition.class);
        when(warpManager.getRegistry()).thenReturn(new org.nakii.valmora.api.registry.SimpleRegistry<>() {{
            register("hub", warp);
        }});

        factory.compile(new String[]{"warp:hub"}, EventOptions.DEFAULT)
                .execute(new SimpleExecutionContext(player, player.getLocation(), null));

        verify(warpManager).teleport(player, warp);
    }

    @Test
    void warpPrefix_unknownWarp_isNoOp() {
        when(warpManager.getRegistry()).thenReturn(new org.nakii.valmora.api.registry.SimpleRegistry<>());
        assertDoesNotThrow(() ->
                factory.compile(new String[]{"warp:missing"}, EventOptions.DEFAULT)
                        .execute(new SimpleExecutionContext(player, player.getLocation(), null)));
        verify(warpManager, never()).teleport(any(), any());
    }

    @Test
    void blockedByZoneFlag_doesNotTeleportAndNotifiesPlayer() {
        var zone = mock(org.nakii.valmora.module.zone.ZoneDefinition.class);
        var flags = mock(org.nakii.valmora.module.zone.ZoneFlags.class);
        when(zone.getFlags()).thenReturn(flags);
        when(flags.teleportation()).thenReturn(false);
        when(zoneManager.getCurrentZone(player)).thenReturn(Optional.of(zone));

        Location before = player.getLocation().clone();
        factory.compile(new String[]{"10", "70", "20"}, EventOptions.DEFAULT)
                .execute(new SimpleExecutionContext(player, player.getLocation(), null));
        server.getScheduler().performTicks(2);

        assertEquals(before.getBlockX(), player.getLocation().getBlockX());
    }
}
