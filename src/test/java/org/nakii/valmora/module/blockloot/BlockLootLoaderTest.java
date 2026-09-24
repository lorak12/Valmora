package org.nakii.valmora.module.blockloot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.Valmora;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link BlockLootLoader} through a real {@code YamlLoader} pass over files on disk,
 * following {@code YamlLoaderPackNamespacingTest}'s harness style.
 */
class BlockLootLoaderTest {

    private Valmora mockPlugin(Path dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("BlockLootLoaderTest"));
        return plugin;
    }

    @Test
    void parsesMaterialAndDropsFromTheSectionNotTheId(@TempDir Path dataFolder) throws Exception {
        Path dir = dataFolder.resolve("block_loot");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("stone.yml"), """
                stone_override:
                  material: STONE
                  drops:
                    - item: iron_dust
                      min: 1
                      max: 2
                      chance: 1.0
                    - item: DIAMOND
                      min: 1
                      max: 1
                      chance: 0.02
                """);

        Valmora plugin = mockPlugin(dataFolder);
        BlockLootRegistry registry = new BlockLootRegistry(plugin);
        new BlockLootLoader(plugin, registry).load();

        BlockLootConfig config = registry.get(Material.STONE);
        assertEquals(Material.STONE, config.material());
        assertEquals(2, config.drops().size());
        assertEquals("iron_dust", config.drops().get(0).itemId());
        assertEquals(1, config.drops().get(0).minAmount());
        assertEquals(2, config.drops().get(0).maxAmount());
        assertEquals(1.0, config.drops().get(0).chance());
        assertEquals("DIAMOND", config.drops().get(1).itemId());
        assertEquals(0.02, config.drops().get(1).chance());
    }

    @Test
    void missingMaterialKey_failsToLoad(@TempDir Path dataFolder) throws Exception {
        Path dir = dataFolder.resolve("block_loot");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("bad.yml"), """
                no_material:
                  drops:
                    - item: iron_dust
                """);

        Valmora plugin = mockPlugin(dataFolder);
        BlockLootRegistry registry = new BlockLootRegistry(plugin);
        new BlockLootLoader(plugin, registry).load();

        assertEquals(0, registry.size());
    }

    @Test
    void unresolvableMaterial_failsToLoadRatherThanSilentlyMatchingNothing(@TempDir Path dataFolder) throws Exception {
        Path dir = dataFolder.resolve("block_loot");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("bad.yml"), """
                bad_material:
                  material: NOT_A_REAL_MATERIAL
                  drops:
                    - item: iron_dust
                """);

        Valmora plugin = mockPlugin(dataFolder);
        BlockLootRegistry registry = new BlockLootRegistry(plugin);
        new BlockLootLoader(plugin, registry).load();

        assertEquals(0, registry.size());
        assertNull(registry.get(Material.STONE));
    }

    @Test
    void twoIdsTargetingTheSameMaterial_onlyTheFirstWins(@TempDir Path dataFolder) throws Exception {
        Path dir = dataFolder.resolve("block_loot");
        Files.createDirectories(dir);
        // A single file, two top-level ids both targeting STONE — proves the id/material decoupling
        // (the namespacing fix) doesn't silently let a second config clobber the first.
        Files.writeString(dir.resolve("collide.yml"), """
                first_stone:
                  material: STONE
                  drops:
                    - item: a
                second_stone:
                  material: STONE
                  drops:
                    - item: b
                """);

        Valmora plugin = mockPlugin(dataFolder);
        BlockLootRegistry registry = new BlockLootRegistry(plugin);
        new BlockLootLoader(plugin, registry).load();

        assertEquals(1, registry.size());
    }
}
