package org.nakii.valmora.module.resource;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.ItemRegistry;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.zone.ResourceStage;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneFlags;
import org.nakii.valmora.module.zone.ZoneManager;
import org.nakii.valmora.module.zone.ZoneResourceConfig;
import org.nakii.valmora.module.zone.ZoneResourceDrop;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for {@link ResourceManager#handleBlockBreak} — written against the
 * pre-flow implementation and kept unchanged after the move to {@code resource_pipeline.yml}'s
 * default {@code break} flow (docs/PIPELINE_FLOWS_PLAN.md §6 steps 1 and 7).
 */
class ResourceBreakFlowTest {

    private Valmora plugin;
    private YamlConfiguration config;
    private ScriptModule scriptModule;
    private ZoneManager zoneManager;
    private ItemRegistry itemRegistry;
    private BukkitScheduler scheduler;
    private Player player;
    private PlayerInventory inventory;
    private Block block;
    private ResourceManager manager;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;
    private Material blockType = Material.IRON_ORE;

    @BeforeEach
    void setUp() throws Exception {
        File dataFolder = Files.createTempDirectory("valmora-resource").toFile();
        plugin = mock(Valmora.class);
        config = new YamlConfiguration();
        // Silk Touch needs the enchantment registry (a running server) — covered in-game only.
        config.set("resource.silk-touch.enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ResourceBreakFlowTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(plugin.getServer()).thenReturn(server);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));

        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(api.getPlayerManager()).thenReturn(mock(PlayerManager.class)); // no session → 0 power/fortune
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        zoneManager = mock(ZoneManager.class);
        when(zoneManager.getZoneAt(any())).thenReturn(Optional.empty());
        when(plugin.getZoneManager()).thenReturn(zoneManager);

        ItemManager itemManager = mock(ItemManager.class);
        itemRegistry = mock(ItemRegistry.class);
        when(itemManager.getItemRegistry()).thenReturn(itemRegistry);
        when(plugin.getItemManager()).thenReturn(itemManager);

        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("miner");

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        block = mock(Block.class);
        when(block.getLocation()).thenReturn(loc);
        when(block.getType()).thenAnswer(inv -> blockType);
        doAnswer(inv -> { blockType = inv.getArgument(0); return null; }).when(block).setType(any(Material.class), anyBoolean());

        manager = new ResourceManager(plugin);
        new ResourcePipelineLoader(plugin, scriptModule).load();
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    private void zone(ZoneResourceConfig resource) {
        ZoneDefinition zone = new ZoneDefinition("mine", "<gray>Mine", "world",
                0, 0, 0, 10, 10, 10, List.of(), ZoneFlags.defaults(), null,
                new ArrayList<>(), Map.of(Material.IRON_ORE, resource), new ArrayList<>(), new ArrayList<>());
        when(zoneManager.getZoneAt(any())).thenReturn(Optional.of(zone));
    }

    private ItemStack stockItem(String id) {
        ItemStack stack = mock(ItemStack.class);
        when(itemRegistry.createItemStack(id)).thenReturn(Optional.of(stack));
        return stack;
    }

    private static ZoneResourceConfig singleStage(double requiredPower, int regenTicks, int amount) {
        return new ZoneResourceConfig(regenTicks,
                List.of(new ResourceStage(List.of(new ZoneResourceDrop("iron_chunk", amount, amount, 1.0)), Material.STONE)),
                requiredPower);
    }

    @Test
    void outsideAnyZone_isNotTracked() {
        assertEquals(ResourceManager.BreakResult.NOT_TRACKED, manager.handleBlockBreak(player, block));
    }

    @Test
    void unconfiguredMaterial_isNotTracked() {
        zone(singleStage(0, 100, 1));
        blockType = Material.COAL_ORE;
        assertEquals(ResourceManager.BreakResult.NOT_TRACKED, manager.handleBlockBreak(player, block));
    }

    @Test
    void notEnoughBreakingPower_isRefused() {
        stockItem("iron_chunk");
        zone(singleStage(10, 100, 1));
        assertEquals(ResourceManager.BreakResult.INSUFFICIENT_POWER, manager.handleBlockBreak(player, block));
        verify(inventory, never()).addItem(any());
        verify(block, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void handledBreak_givesDropsReplacesTheBlockAndSchedulesRegen() {
        ItemStack chunk = stockItem("iron_chunk");
        zone(singleStage(0, 100, 2));

        assertEquals(ResourceManager.BreakResult.HANDLED, manager.handleBlockBreak(player, block));
        verify(chunk).setAmount(2);
        verify(inventory).addItem(chunk);
        verify(block).setType(Material.STONE, false);
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(100L));
        assertTrue(manager.isTrackedResource(block.getLocation()));
    }

    @Test
    void depletedBlock_isRefusedUntilRegen() {
        stockItem("iron_chunk");
        zone(singleStage(0, 100, 1));
        manager.handleBlockBreak(player, block);
        assertEquals(ResourceManager.BreakResult.DEPLETED, manager.handleBlockBreak(player, block));
    }

    @Test
    void multiStage_progressesThroughStages() {
        ItemStack first = stockItem("raw_one");
        ItemStack second = stockItem("raw_two");
        zone(new ZoneResourceConfig(100, List.of(
                new ResourceStage(List.of(new ZoneResourceDrop("raw_one", 1, 1, 1.0)), Material.DEEPSLATE),
                new ResourceStage(List.of(new ZoneResourceDrop("raw_two", 1, 1, 1.0)), Material.BEDROCK)), 0));

        assertEquals(ResourceManager.BreakResult.HANDLED, manager.handleBlockBreak(player, block));
        verify(inventory).addItem(first);
        verify(block).setType(Material.DEEPSLATE, false);

        assertEquals(ResourceManager.BreakResult.HANDLED, manager.handleBlockBreak(player, block));
        verify(inventory).addItem(second);
        verify(block).setType(Material.BEDROCK, false);

        assertEquals(ResourceManager.BreakResult.DEPLETED, manager.handleBlockBreak(player, block));
    }

    @Test
    void tooShortRegenDelay_isClamped() {
        stockItem("iron_chunk");
        zone(singleStage(0, 5, 1));
        manager.handleBlockBreak(player, block);
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
    }

    @Test
    void preBreakInterrupt_cancelsTheBreak() {
        ItemStack chunk = stockItem("iron_chunk");
        zone(singleStage(0, 100, 1));
        scriptModule.getHookBus().registerHook("resource:pre_break", "test-veto", ctx -> StageResult.INTERRUPT);

        assertEquals(ResourceManager.BreakResult.INTERRUPTED, manager.handleBlockBreak(player, block));
        verify(inventory, never()).addItem(chunk);
        verify(block, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void postBreakHook_seesMaterialAndStage() {
        stockItem("iron_chunk");
        zone(singleStage(0, 100, 1));
        Object[] seen = new Object[2];
        scriptModule.getHookBus().registerHook("resource:post_break", "test-observer", ctx -> {
            seen[0] = ctx.get("resource:material");
            seen[1] = ctx.get("resource:stage");
            return StageResult.CONTINUE;
        });

        manager.handleBlockBreak(player, block);
        assertEquals("IRON_ORE", seen[0]);
        assertEquals(0, seen[1]);
    }
}
