package org.nakii.valmora.module.death;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneFlags;
import org.nakii.valmora.module.zone.ZoneManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link DeathPolicyResolver}'s zone-override-vs-global-default precedence
 *  (VANILLA_CONTROL_AUDIT.md §9 — keepInventory/keepExperience on death). */
public class DeathPolicyResolverTest {

    private Valmora plugin;
    private FileConfiguration config;
    private ZoneManager zoneManager;
    private Player player;
    private Location location;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;
    private MockedStatic<Bukkit> bukkitStatic;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("DeathPolicyResolverTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        ValmoraAPI api = mock(ValmoraAPI.class);
        zoneManager = mock(ZoneManager.class);
        when(api.getZoneManager()).thenReturn(zoneManager);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        bukkitStatic = mockStatic(Bukkit.class);

        player = mock(Player.class);
        location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);
        when(zoneManager.getZoneAt(location)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
        bukkitStatic.close();
    }

    private ZoneDefinition zoneWithOverride(Boolean keepInventory, Boolean keepExperience) {
        ZoneFlags flags = new ZoneFlags(false, false, false, false, true, true, true, true,
                keepInventory, keepExperience, true, true);
        // A real ZoneDefinition, not a mock — matches ZoneDefinitionTest/ZoneManagerResourceBlockTest's
        // own convention (this concrete class' fields make it awkward to mock cleanly).
        return new ZoneDefinition("test-zone", "<green>Test Zone", "world",
                0, 0, 0, 10, 10, 10, java.util.List.of(), flags, null,
                new java.util.ArrayList<>(), java.util.Map.of(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
    }

    @Test
    void noZoneFallsBackToGlobalDefaults() {
        config.set("death.keep-inventory-default", true);
        config.set("death.keep-experience-default", false);

        DeathPolicyResolver.Policy policy = DeathPolicyResolver.resolve(player);

        assertTrue(policy.keepInventory());
        assertFalse(policy.keepExperience());
    }

    @Test
    void zoneOverrideWinsOverGlobalDefault() {
        config.set("death.keep-inventory-default", false);
        config.set("death.keep-experience-default", false);
        when(zoneManager.getZoneAt(location)).thenReturn(Optional.of(zoneWithOverride(true, true)));

        DeathPolicyResolver.Policy policy = DeathPolicyResolver.resolve(player);

        assertTrue(policy.keepInventory());
        assertTrue(policy.keepExperience());
    }

    @Test
    void nullZoneOverrideInheritsGlobalDefault() {
        config.set("death.keep-inventory-default", true);
        config.set("death.keep-experience-default", false);
        when(zoneManager.getZoneAt(location)).thenReturn(Optional.of(zoneWithOverride(null, null)));

        DeathPolicyResolver.Policy policy = DeathPolicyResolver.resolve(player);

        assertTrue(policy.keepInventory());
        assertFalse(policy.keepExperience());
    }

    @Test
    void mixedZoneOverrideOnlyAppliesTheSetHalf() {
        config.set("death.keep-inventory-default", false);
        config.set("death.keep-experience-default", false);
        // Inventory explicitly overridden true, experience left to inherit (still false).
        when(zoneManager.getZoneAt(location)).thenReturn(Optional.of(zoneWithOverride(true, null)));

        DeathPolicyResolver.Policy policy = DeathPolicyResolver.resolve(player);

        assertTrue(policy.keepInventory());
        assertFalse(policy.keepExperience());
    }

    @Test
    void resolveRespawnOverrideReturnsNullWhenUnset() {
        assertNull(DeathPolicyResolver.resolveRespawnOverride("test-zone"));
    }

    @Test
    void resolveRespawnOverrideReturnsNullWhenMalformed() {
        config.set("death.zone-respawn-overrides.test-zone", "world,1,2"); // missing z
        assertNull(DeathPolicyResolver.resolveRespawnOverride("test-zone"));
    }

    @Test
    void resolveRespawnOverrideParsesWorldCoordinatesAndYaw() {
        World world = mock(World.class);
        bukkitStatic.when(() -> Bukkit.getWorld("arena")).thenReturn(world);
        config.set("death.zone-respawn-overrides.test-zone", "arena,100.5,64,200.5,180");

        Location result = DeathPolicyResolver.resolveRespawnOverride("test-zone");

        assertNotNull(result);
        assertEquals(world, result.getWorld());
        assertEquals(100.5, result.getX());
        assertEquals(64.0, result.getY());
        assertEquals(200.5, result.getZ());
        assertEquals(180f, result.getYaw());
    }
}
