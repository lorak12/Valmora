package org.nakii.valmora.module.reforge;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.Rarity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 4.4 of the refactor (docs/REFACTOR/PROGRESS.md): {@link ForgeCostRegistry}
 * replaces {@link ReforgeModule}'s hardcoded {@code RARITY_COST} static {@code EnumMap}.
 */
public class ForgeCostRegistryTest {

    private Valmora mockPlugin(File dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ForgeCostRegistryTest"));
        return plugin;
    }

    @Test
    void missingFileReproducesTheExactPreRefactorCostTable() throws IOException {
        ForgeCostRegistry registry = new ForgeCostRegistry();
        registry.load(mockPlugin(Files.createTempDirectory("valmora-test").toFile()));

        assertEquals(250, registry.getCost(Rarity.COMMON));
        assertEquals(500, registry.getCost(Rarity.UNCOMMON));
        assertEquals(1000, registry.getCost(Rarity.RARE));
        assertEquals(2500, registry.getCost(Rarity.EPIC));
        assertEquals(5000, registry.getCost(Rarity.LEGENDARY));
        assertEquals(10000, registry.getCost(Rarity.MYTHIC));
        assertEquals(15000, registry.getCost(Rarity.DIVINE));
    }

    @Test
    void nullRarityFallsBackToTheDefaultCost() {
        ForgeCostRegistry registry = new ForgeCostRegistry();
        assertEquals(250, registry.getCost(null));
    }

    @Test
    void adminOverrideTakesPrecedenceForThatRarityOnly() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File enchantDir = new File(dataFolder, "enchant");
        enchantDir.mkdirs();
        Files.writeString(new File(enchantDir, "forge_costs.yml").toPath(),
                "forge_costs:\n  COMMON: 100\n");

        ForgeCostRegistry registry = new ForgeCostRegistry();
        registry.load(mockPlugin(dataFolder));

        assertEquals(100, registry.getCost(Rarity.COMMON));
        // Untouched rarities fall back to their built-in default, not the override or 0.
        assertEquals(500, registry.getCost(Rarity.UNCOMMON));
    }

    @Test
    void clearMakesEveryLookupFallBackToTheDefaultCost() throws IOException {
        ForgeCostRegistry registry = new ForgeCostRegistry();
        registry.load(mockPlugin(Files.createTempDirectory("valmora-test").toFile()));
        registry.clear();

        assertEquals(250, registry.getCost(Rarity.LEGENDARY));
    }
}
