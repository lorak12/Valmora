package org.nakii.valmora.module.recipe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 4.3 of the refactor (docs/REFACTOR/PROGRESS.md): {@link AnvilTemplateRegistry}
 * replaces {@link AnvilMachineHandler}'s hardcoded merge-cost formula. Default was rescaled from
 * "10 coins per level" to "2 XP levels per level" when the anvil unification (coworker anvil spec)
 * switched the whole cost model from coins to XP levels.
 *
 * <p>Tunables now live under {@code anvil:} in {@code config.yml} rather than a standalone
 * {@code recipes/anvil_templates.yml} (see CLAUDE.md §9.3), so tests drive {@link
 * Valmora#getConfig()} directly instead of writing a file.
 */
public class AnvilTemplateRegistryTest {

    private Valmora mockPlugin(String yaml) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AnvilTemplateRegistryTest"));
        return plugin;
    }

    @Test
    void missingKeysKeepThePreRefactorDefault() {
        AnvilTemplateRegistry registry = new AnvilTemplateRegistry();
        registry.load(mockPlugin(""));

        assertEquals(2, registry.getMergeCostPerLevel());
    }

    @Test
    void adminOverrideIsHonored() {
        AnvilTemplateRegistry registry = new AnvilTemplateRegistry();
        registry.load(mockPlugin("anvil:\n  templates:\n    merge:\n      cost-per-level: 25\n"));

        assertEquals(25, registry.getMergeCostPerLevel());
    }

    @Test
    void clearResetsToTheDefault() {
        AnvilTemplateRegistry registry = new AnvilTemplateRegistry();
        registry.load(mockPlugin("anvil:\n  templates:\n    merge:\n      cost-per-level: 99\n"));
        registry.clear();

        assertEquals(2, registry.getMergeCostPerLevel());
    }
}
