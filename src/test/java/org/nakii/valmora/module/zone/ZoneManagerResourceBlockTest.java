package org.nakii.valmora.module.zone;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link ZoneManager#addResourceBlock}/{@link ZoneManager#removeResourceBlock} — added per
 * docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "resource-block editing via /zone" item (previously
 * resource-blocks could only be authored by hand-editing zone YAML).
 */
public class ZoneManagerResourceBlockTest {

    private File dataFolder;
    private Valmora plugin;
    private ZoneRegistry registry;
    private ZoneManager manager;

    @BeforeEach
    void setUp() throws IOException {
        dataFolder = Files.createTempDirectory("zone-manager-test").toFile();
        plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ZoneManagerResourceBlockTest"));

        registry = new ZoneRegistry();
        manager = new ZoneManager(plugin, registry);

        ZoneDefinition zone = new ZoneDefinition("testzone", "<green>Test Zone", "world",
                0, 0, 0, 10, 10, 10, List.of(), ZoneFlags.defaults(), null,
                new java.util.ArrayList<>(), java.util.Map.of(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
        registry.register("testzone", zone);
    }

    @AfterEach
    void tearDown() {
        deleteRecursive(dataFolder);
    }

    private void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        file.delete();
    }

    @Test
    void addResourceBlockRegistersConfig() {
        ZoneDefinition updated = manager.addResourceBlock("testzone", Material.COAL_ORE, 200, 0.0, "COAL", 1, 3, 1.0);

        assertNotNull(updated);
        assertTrue(updated.getResourceBlocks().containsKey(Material.COAL_ORE));
        ZoneResourceConfig cfg = updated.getResourceBlocks().get(Material.COAL_ORE);
        assertEquals(200, cfg.getRegenDelayTicks());
        assertEquals(1, cfg.getStageCount());
        assertEquals("COAL", cfg.getStage(0).getDrops().get(0).getItemId());
        assertEquals(1, cfg.getStage(0).getDrops().get(0).getMinAmount());
        assertEquals(3, cfg.getStage(0).getDrops().get(0).getMaxAmount());

        // Registry reflects the update too (not just the returned copy).
        assertTrue(registry.get("testzone").get().getResourceBlocks().containsKey(Material.COAL_ORE));
    }

    @Test
    void addResourceBlockUnknownZoneReturnsNull() {
        assertNull(manager.addResourceBlock("nope", Material.COAL_ORE, 200, 0.0, "COAL", 1, 1, 1.0));
    }

    @Test
    void addResourceBlockWritesFileToDisk() {
        manager.addResourceBlock("testzone", Material.COAL_ORE, 200, 0.0, "COAL", 1, 3, 1.0);
        File file = new File(dataFolder, "zones/testzone.yml");
        assertTrue(file.exists());
    }

    @Test
    void removeResourceBlockRemovesConfig() {
        manager.addResourceBlock("testzone", Material.COAL_ORE, 200, 0.0, "COAL", 1, 3, 1.0);
        boolean removed = manager.removeResourceBlock("testzone", Material.COAL_ORE);
        assertTrue(removed);
        assertFalse(registry.get("testzone").get().getResourceBlocks().containsKey(Material.COAL_ORE));
    }

    @Test
    void removeResourceBlockNotPresentReturnsFalse() {
        boolean removed = manager.removeResourceBlock("testzone", Material.DIAMOND_ORE);
        assertFalse(removed);
    }

    @Test
    void removeResourceBlockUnknownZoneReturnsFalse() {
        assertFalse(manager.removeResourceBlock("nope", Material.COAL_ORE));
    }
}
