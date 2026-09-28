package org.nakii.valmora.module.death;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneFlags;
import org.nakii.valmora.module.zone.ZoneManager;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for {@link DeathListener} — written against the pre-flow implementation
 * and kept unchanged after the move to {@code death_pipeline.yml}'s default {@code death}/
 * {@code respawn} flows (docs/PIPELINE_FLOWS_PLAN.md §6 steps 1 and 7).
 */
class DeathFlowTest {

    private Valmora plugin;
    private YamlConfiguration config;
    private ScriptModule scriptModule;
    private ZoneManager zoneManager;
    private Player player;
    private DeathListener listener;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;
    private MockedStatic<Bukkit> bukkitStatic;

    @BeforeEach
    void setUp() throws Exception {
        File dataFolder = Files.createTempDirectory("valmora-death").toFile();
        plugin = mock(Valmora.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DeathFlowTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        when(api.getScriptModule()).thenReturn(scriptModule);
        zoneManager = mock(ZoneManager.class);
        when(zoneManager.getZoneAt(any())).thenReturn(Optional.empty());
        when(api.getZoneManager()).thenReturn(zoneManager);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);
        bukkitStatic = mockStatic(Bukkit.class);

        player = mock(Player.class);
        when(player.getName()).thenReturn("Steve");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(mock(Location.class));

        listener = new DeathListener(plugin);
        new DeathPipelineLoader(plugin, scriptModule).load();
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
        bukkitStatic.close();
    }

    private PlayerDeathEvent deathEvent(List<ItemStack> drops) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);
        return event;
    }

    @Test
    void defaults_keepNothingAndBroadcastTheMessage() {
        List<ItemStack> drops = new ArrayList<>(List.of(mock(ItemStack.class)));
        PlayerDeathEvent event = deathEvent(drops);

        listener.onDeath(event);

        verify(event).deathMessage(any(Component.class));
        verify(event).setKeepInventory(false);
        verify(event).setKeepLevel(false);
        verify(event, never()).setDroppedExp(anyInt());
        assertEquals(1, drops.size());
    }

    @Test
    void keepInventoryAndExperience_clearDropsAndExp() {
        config.set("death.keep-inventory-default", true);
        config.set("death.keep-experience-default", true);
        List<ItemStack> drops = new ArrayList<>(List.of(mock(ItemStack.class)));
        PlayerDeathEvent event = deathEvent(drops);

        listener.onDeath(event);

        verify(event).setKeepInventory(true);
        verify(event).setKeepLevel(true);
        verify(event).setDroppedExp(0);
        assertTrue(drops.isEmpty());
    }

    @Test
    void zoneFlagOverridesTheDefault() {
        ZoneFlags flags = new ZoneFlags(false, false, false, false, true, true, true, true,
                true, null, true, true);
        ZoneDefinition zone = new ZoneDefinition("arena", "<red>Arena", "world",
                0, 0, 0, 10, 10, 10, List.of(), flags, null,
                new ArrayList<>(), Map.of(), new ArrayList<>(), new ArrayList<>());
        when(zoneManager.getZoneAt(any())).thenReturn(Optional.of(zone));
        PlayerDeathEvent event = deathEvent(new ArrayList<>());

        listener.onDeath(event);

        verify(event).setKeepInventory(true);
        verify(event).setKeepLevel(false);
    }

    @Test
    void broadcastDisabled_suppressesTheMessage() {
        config.set("death.broadcast-enabled", false);
        PlayerDeathEvent event = deathEvent(new ArrayList<>());

        listener.onDeath(event);

        verify(event).deathMessage(null);
    }

    @Test
    void onDeathHookRuns() {
        boolean[] ran = {false};
        scriptModule.getHookBus().registerHook("player:on_death", "test", ctx -> {
            ran[0] = true;
            return StageResult.CONTINUE;
        });

        listener.onDeath(deathEvent(new ArrayList<>()));
        assertTrue(ran[0]);
    }

    @Test
    void respawn_zoneOverrideMovesTheRespawnAndHookSeesSpawnKind() {
        ZoneDefinition zone = new ZoneDefinition("arena", "<red>Arena", "world",
                0, 0, 0, 10, 10, 10, List.of(), ZoneFlags.defaults(), null,
                new ArrayList<>(), Map.of(), new ArrayList<>(), new ArrayList<>());
        when(zoneManager.getZoneAt(any())).thenReturn(Optional.of(zone));
        World world = mock(World.class);
        bukkitStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        config.set("death.zone-respawn-overrides.arena", "world,1,64,2");
        Object[] seen = new Object[1];
        scriptModule.getHookBus().registerHook("player:on_respawn", "test", ctx -> {
            seen[0] = ctx.get("player:is_bed_spawn");
            return StageResult.CONTINUE;
        });
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.isBedSpawn()).thenReturn(true);

        listener.onRespawn(event);

        verify(event).setRespawnLocation(argThat(loc -> loc.getX() == 1 && loc.getY() == 64 && loc.getZ() == 2));
        assertEquals(Boolean.TRUE, seen[0]);
    }
}
