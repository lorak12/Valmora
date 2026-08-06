package org.nakii.valmora.module.pet;

import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the Phase 3 Task 11 gap flagged in docs/REFACTOR/PROGRESS.md: {@link PetDefinition}'s
 * pre-computed XP threshold table, driven by {@code pets/defaults.yml} and per-pet overrides.
 */
public class PetXpFormulaTest {

    private Valmora plugin;
    private File dataFolder;

    @BeforeEach
    void setUp() throws IOException {
        // $curve.level$ resolution chain (same pattern as XpCurveRegistryTest).
        ValmoraAPI api = mock(ValmoraAPI.class);
        ScriptModule apiScriptModule = mock(ScriptModule.class);
        VariableResolver variableResolver = mock(VariableResolver.class);
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(apiScriptModule);
        when(apiScriptModule.getVariableResolver()).thenReturn(variableResolver);
        when(variableResolver.resolve(eq("$curve.level$"), any())).thenAnswer(inv -> {
            ExecutionContext ctx = inv.getArgument(1);
            return ctx.get("curve:level");
        });

        dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "pets").mkdirs();

        plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PetXpFormulaTest"));
        ScriptModule pluginScriptModule = mock(ScriptModule.class);
        when(pluginScriptModule.getExpressionParser()).thenReturn(new ExpressionParser());
        when(plugin.getScriptModule()).thenReturn(pluginScriptModule);

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
    }

    private void writePetFile(String yaml) throws IOException {
        Files.writeString(new File(dataFolder, "pets/test.yml").toPath(), yaml);
    }

    @Test
    void defaultFormulaMatchesTheExactPreRefactorHundredTimesLevelSquared() throws IOException {
        writePetFile("test_pet:\n  name: Test Pet\n  entity-type: WOLF\n");

        PetModule module = new PetModule(plugin);
        module.onEnable();

        PetDefinition def = module.getDefinition("test_pet");
        assertNotNull(def);
        assertEquals(100L, def.xpForLevel(1));   // 100 * 1^2
        assertEquals(400L, def.xpForLevel(2));   // 100 * 2^2
        assertEquals(900L, def.xpForLevel(3));   // 100 * 3^2
        assertEquals(200, def.getMaxLevel());    // default max-level
    }

    @Test
    void perPetOverrideTakesPrecedenceOverTheServerWideDefault() throws IOException {
        writePetFile("test_pet:\n  name: Test Pet\n  entity-type: WOLF\n"
                + "  xp-formula: \"50 * $curve.level$\"\n  max-level: 10\n");

        PetModule module = new PetModule(plugin);
        module.onEnable();

        PetDefinition def = module.getDefinition("test_pet");
        assertEquals(10, def.getMaxLevel());
        assertEquals(50L, def.xpForLevel(1));
        assertEquals(500L, def.xpForLevel(10));
    }

    @Test
    void serverWideDefaultsFileOverridesTheHardcodedFallback() throws IOException {
        Files.writeString(new File(dataFolder, "pets/defaults.yml").toPath(),
                "pet_defaults:\n  xp-formula: \"10 * $curve.level$\"\n  max-level: 5\n");
        writePetFile("test_pet:\n  name: Test Pet\n  entity-type: WOLF\n");

        PetModule module = new PetModule(plugin);
        module.onEnable();

        PetDefinition def = module.getDefinition("test_pet");
        assertEquals(5, def.getMaxLevel());
        assertEquals(10L, def.xpForLevel(1));
        assertEquals(50L, def.xpForLevel(5));

        // The pets/defaults.yml "pet_defaults" top-level key must not itself become a registered pet.
        assertNull(module.getDefinition("pet_defaults"));
    }

    @Test
    void xpForLevelClampsBelowOneAndAboveMaxLevel() throws IOException {
        writePetFile("test_pet:\n  name: Test Pet\n  entity-type: WOLF\n  max-level: 3\n");

        PetModule module = new PetModule(plugin);
        module.onEnable();

        PetDefinition def = module.getDefinition("test_pet");
        assertEquals(0L, def.xpForLevel(0));
        assertEquals(def.xpForLevel(3), def.xpForLevel(999)); // clamps to the top of the table
    }
}
