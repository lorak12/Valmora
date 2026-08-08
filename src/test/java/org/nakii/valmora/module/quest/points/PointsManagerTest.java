package org.nakii.valmora.module.quest.points;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link PointsManager} — previously untested (per docs/IMPLEMENTATION_BACKLOG.md's
 *  cross-cutting "unit tests for untested modules" item). Points are stored as
 *  {@code point.<category>} profile variables — this exercises the get/set/add/take arithmetic
 *  and the no-profile no-op guard. */
public class PointsManagerTest {

    private ValmoraAPI api;
    private PlayerManager playerManager;
    private ValmoraPlayer session;
    private ValmoraProfile profile;
    private Map<String, Object> variables;
    private UUID uuid;
    private MockedStatic<Bukkit> bukkitStatic;
    private PointsManager manager;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        api = mock(ValmoraAPI.class);
        playerManager = mock(PlayerManager.class);
        session = mock(ValmoraPlayer.class);
        profile = mock(ValmoraProfile.class);
        variables = new HashMap<>();

        when(api.getPlayerManager()).thenReturn(playerManager);
        when(playerManager.getSession(uuid)).thenReturn(session);
        when(session.getActiveProfile()).thenReturn(profile);
        when(profile.getVariables()).thenReturn(variables);
        ValmoraAPI.setProvider(api);

        bukkitStatic = mockStatic(Bukkit.class);
        bukkitStatic.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null); // no live player by default
        PluginManager pluginManager = mock(PluginManager.class);
        bukkitStatic.when(Bukkit::getPluginManager).thenReturn(pluginManager);

        manager = new PointsManager();
    }

    @AfterEach
    void tearDown() {
        bukkitStatic.close();
    }

    @Test
    void getPointsReturnsZeroWhenNoVariableSet() {
        assertEquals(0, manager.getPoints(uuid, "mining"));
    }

    @Test
    void getPointsReturnsZeroWhenNoActiveProfile() {
        when(session.getActiveProfile()).thenReturn(null);
        assertEquals(0, manager.getPoints(uuid, "mining"));
    }

    @Test
    void getPointsReturnsZeroWhenNoSession() {
        when(playerManager.getSession(uuid)).thenReturn(null);
        assertEquals(0, manager.getPoints(uuid, "mining"));
    }

    @Test
    void setPointsStoresLowercasedCategoryKey() {
        manager.setPoints(uuid, "MINING", 50);
        assertEquals(50, variables.get("point.mining"));
    }

    @Test
    void setPointsIsNoOpWhenNoActiveProfile() {
        when(session.getActiveProfile()).thenReturn(null);
        manager.setPoints(uuid, "mining", 50);
        assertTrue(variables.isEmpty());
    }

    @Test
    void setPointsFiresEventWhenPlayerIsOnline() {
        Player onlinePlayer = mock(Player.class);
        bukkitStatic.when(() -> Bukkit.getPlayer(uuid)).thenReturn(onlinePlayer);
        PluginManager pluginManager = Bukkit.getPluginManager();

        manager.setPoints(uuid, "mining", 25);

        verify(pluginManager).callEvent(any(PointsChangedEvent.class));
    }

    @Test
    void setPointsDoesNotFireEventWhenPlayerOffline() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        manager.setPoints(uuid, "mining", 25);
        verify(pluginManager, never()).callEvent(any());
    }

    @Test
    void addPointsAccumulates() {
        manager.addPoints(uuid, "mining", 10);
        manager.addPoints(uuid, "mining", 15);
        assertEquals(25, manager.getPoints(uuid, "mining"));
    }

    @Test
    void takePointsSubtracts() {
        manager.setPoints(uuid, "mining", 30);
        manager.takePoints(uuid, "mining", 10);
        assertEquals(20, manager.getPoints(uuid, "mining"));
    }

    @Test
    void takePointsClampsAtZero() {
        manager.setPoints(uuid, "mining", 5);
        manager.takePoints(uuid, "mining", 100);
        assertEquals(0, manager.getPoints(uuid, "mining"));
    }

    @Test
    void categoriesAreIndependent() {
        manager.setPoints(uuid, "mining", 10);
        manager.setPoints(uuid, "combat", 20);
        assertEquals(10, manager.getPoints(uuid, "mining"));
        assertEquals(20, manager.getPoints(uuid, "combat"));
    }
}
