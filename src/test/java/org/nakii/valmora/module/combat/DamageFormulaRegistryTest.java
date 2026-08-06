package org.nakii.valmora.module.combat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 2.2 of the refactor (docs/REFACTOR/PROGRESS.md): {@link DamageFormulaRegistry}
 * pre-compiles combat formulas once at load time and must reproduce the exact pre-refactor
 * hardcoded math by default (backward compatibility — CLAUDE.md design rule §6).
 *
 * <p>Follows the same mocking pattern as {@code ExpressionTest}: rather than wiring a live
 * {@code DamageVariableProvider} through a real {@code ScriptModule}, the {@link VariableResolver}
 * is stubbed directly for the exact {@code $dmg.*$} strings each formula uses.
 */
public class DamageFormulaRegistryTest {

    private final ValmoraAPI api = mock(ValmoraAPI.class);
    private final ScriptModule scriptModule = mock(ScriptModule.class);
    private final VariableResolver variableResolver = mock(VariableResolver.class);

    @BeforeEach
    void setUp() {
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(variableResolver);
    }

    private ExecutionContext stubVariables(double strength, double critDamage, double defense) {
        ExecutionContext ctx = new SimpleExecutionContext(null, null, null);
        when(variableResolver.resolve("$dmg.strength$", ctx)).thenReturn(strength);
        when(variableResolver.resolve("$dmg.crit_damage$", ctx)).thenReturn(critDamage);
        when(variableResolver.resolve("$dmg.defense$", ctx)).thenReturn(defense);
        return ctx;
    }

    private DamageFormulaRegistry registryOver(File dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("DamageFormulaRegistryTest"));
        return new DamageFormulaRegistry(plugin, new ExpressionParser());
    }

    @Test
    void defaultFormulasReproduceThePreRefactorHardcodedMath() throws IOException {
        DamageFormulaRegistry registry = registryOver(Files.createTempDirectory("valmora-test").toFile());
        registry.load(); // no damage_formula.yml present -> pure defaults

        ExecutionContext ctx = stubVariables(50.0, 50.0, 300.0);

        // 1 + strength/100 = 1 + 0.5 = 1.5
        assertEquals(1.5, registry.evaluate(DamageFormulaRegistry.DAMAGE_MULTIPLIER, ctx, -1));
        // 1 + critDamage/100 = 1.5
        assertEquals(1.5, registry.evaluate(DamageFormulaRegistry.CRIT_MULTIPLIER, ctx, -1));
        // 100 / (defense + 100) = 100/400 = 0.25
        assertEquals(0.25, registry.evaluate(DamageFormulaRegistry.DEFENSE_MULTIPLIER, ctx, -1));
    }

    @Test
    void missingFormulaFallsBackToProvidedDefaultRatherThanZero() throws IOException {
        DamageFormulaRegistry registry = registryOver(Files.createTempDirectory("valmora-test").toFile());
        // load() never called — registry is empty.

        assertEquals(42.0, registry.evaluate("nonexistent", stubVariables(0, 0, 0), 42.0));
    }

    @Test
    void adminOverrideInDamageFormulaYamlTakesPrecedenceOverTheDefault() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        Files.writeString(new File(dataFolder, "damage_formula.yml").toPath(),
                DamageFormulaRegistry.DAMAGE_MULTIPLIER + ": \"2 + $dmg.strength$ / 100\"\n");

        DamageFormulaRegistry registry = registryOver(dataFolder);
        registry.load();

        ExecutionContext ctx = stubVariables(50.0, 0, 300.0);

        // Overridden formula: 2 + 50/100 = 2.5 (not the default 1.5)
        assertEquals(2.5, registry.evaluate(DamageFormulaRegistry.DAMAGE_MULTIPLIER, ctx, -1));
        // Untouched formula still uses its default: 100/(300+100) = 0.25
        assertEquals(0.25, registry.evaluate(DamageFormulaRegistry.DEFENSE_MULTIPLIER, ctx, -1));
    }
}
