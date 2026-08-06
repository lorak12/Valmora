package org.nakii.valmora.module.recipe;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 4.3 of the refactor (docs/REFACTOR/PROGRESS.md): {@link AnvilTemplateRegistry}
 * replaces {@link AnvilMachineHandler}'s hardcoded "10 coins per merged enchant level" formula.
 */
public class AnvilTemplateRegistryTest {

    private Valmora mockPlugin(File dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AnvilTemplateRegistryTest"));
        return plugin;
    }

    @Test
    void missingFileKeepsThePreRefactorDefault() throws IOException {
        AnvilTemplateRegistry registry = new AnvilTemplateRegistry();
        registry.load(mockPlugin(Files.createTempDirectory("valmora-test").toFile()));

        assertEquals(10, registry.getMergeCostPerLevel());
    }

    @Test
    void adminOverrideIsHonored() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File recipesDir = new File(dataFolder, "recipes");
        recipesDir.mkdirs();
        Files.writeString(new File(recipesDir, "anvil_templates.yml").toPath(),
                "templates:\n  merge:\n    cost-per-level: 25\n");

        AnvilTemplateRegistry registry = new AnvilTemplateRegistry();
        registry.load(mockPlugin(dataFolder));

        assertEquals(25, registry.getMergeCostPerLevel());
    }

    @Test
    void clearResetsToTheDefault() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File recipesDir = new File(dataFolder, "recipes");
        recipesDir.mkdirs();
        Files.writeString(new File(recipesDir, "anvil_templates.yml").toPath(),
                "templates:\n  merge:\n    cost-per-level: 99\n");

        AnvilTemplateRegistry registry = new AnvilTemplateRegistry();
        registry.load(mockPlugin(dataFolder));
        registry.clear();

        assertEquals(10, registry.getMergeCostPerLevel());
    }
}
