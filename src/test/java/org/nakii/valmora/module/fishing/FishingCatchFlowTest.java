package org.nakii.valmora.module.fishing;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.StageResult;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.ItemRegistry;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.zone.ZoneManager;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for {@link FishingManager#handleCatch} — written against the pre-flow
 * implementation and kept unchanged after the move to {@code fishing_pipeline.yml}'s default
 * {@code catch} flow (docs/PIPELINE_FLOWS_PLAN.md §6 steps 1 and 7), so the default flow is pinned
 * to the old behaviour.
 */
class FishingCatchFlowTest {

    private Valmora plugin;
    private ScriptModule scriptModule;
    private MobManager mobManager;
    private ItemRegistry itemRegistry;
    private Player player;
    private PlayerInventory inventory;
    private Location hook;
    private FishingManager manager;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;
    private File dataFolder;

    @BeforeEach
    void setUp() throws Exception {
        dataFolder = Files.createTempDirectory("valmora-fishing").toFile();
        plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FishingCatchFlowTest"));
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);

        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        when(api.getScriptModule()).thenReturn(scriptModule);
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);

        ZoneManager zoneManager = mock(ZoneManager.class);
        when(zoneManager.getZoneAt(any())).thenReturn(Optional.empty());
        when(plugin.getZoneManager()).thenReturn(zoneManager);

        mobManager = mock(MobManager.class);
        when(plugin.getMobManager()).thenReturn(mobManager);

        ItemManager itemManager = mock(ItemManager.class);
        itemRegistry = mock(ItemRegistry.class);
        when(itemManager.getItemRegistry()).thenReturn(itemRegistry);
        when(plugin.getItemManager()).thenReturn(itemManager);

        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getName()).thenReturn("angler");
        when(player.getLocation()).thenReturn(mock(Location.class));
        hook = mock(Location.class);

        manager = new FishingManager(plugin);
        new FishingPipelineLoader(plugin, scriptModule).load();
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    private void table(String seaCreature, double chance, FishingLootEntry... entries) {
        manager.getRegistry().register("default", new FishingLootTable("default", List.of(entries), chance, seaCreature));
    }

    private ItemStack stockItem(String id) {
        ItemStack stack = mock(ItemStack.class);
        when(itemRegistry.createItemStack(id)).thenReturn(Optional.of(stack));
        return stack;
    }

    @Test
    void noTable_catchesNothing() {
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
    }

    @Test
    void lootEntry_givesTheRolledItemAndAmount() {
        ItemStack cod = stockItem("cod");
        table(null, 0.0, new FishingLootEntry("cod", 1, 3, 3));

        assertTrue(manager.handleCatch(player, hook));
        verify(cod).setAmount(3);
        verify(inventory).addItem(cod);
    }

    @Test
    void emptyTable_catchesNothing() {
        table(null, 0.0);
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
    }

    @Test
    void seaCreatureRoll_spawnsAtTheHookInsteadOfLoot() {
        stockItem("cod");
        MobDefinition def = mock(MobDefinition.class);
        when(mobManager.getMobDefinition("kraken")).thenReturn(def);
        table("kraken", 1.0, new FishingLootEntry("cod", 1, 1, 1));

        assertTrue(manager.handleCatch(player, hook));
        verify(mobManager).spawnMob(def, hook);
        verify(inventory, never()).addItem(any());
    }

    @Test
    void unknownSeaCreature_catchesNothing() {
        table("missing_mob", 1.0, new FishingLootEntry("cod", 1, 1, 1));
        assertFalse(manager.handleCatch(player, hook));
        verify(mobManager, never()).spawnMob(any(), any());
    }

    @Test
    void preCatchInterrupt_cancelsTheCatch() {
        ItemStack cod = stockItem("cod");
        table(null, 0.0, new FishingLootEntry("cod", 1, 1, 1));
        scriptModule.getHookBus().registerHook("fishing:pre_catch", "test-veto", ctx -> StageResult.INTERRUPT);

        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(cod);
    }

    @Test
    void postCatchHook_seesTheCaughtItem() {
        stockItem("cod");
        table(null, 0.0, new FishingLootEntry("cod", 1, 2, 2));
        Object[] seen = new Object[2];
        scriptModule.getHookBus().registerHook("fishing:post_catch", "test-observer", ctx -> {
            seen[0] = ctx.get("fishing:item");
            seen[1] = ctx.get("fishing:amount");
            return StageResult.CONTINUE;
        });

        assertTrue(manager.handleCatch(player, hook));
        assertEquals("cod", seen[0]);
        assertEquals(2, seen[1]);
    }
}
