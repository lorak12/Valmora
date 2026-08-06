package org.nakii.valmora.module.skill;

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
 * Covers Phase 3.2 of the refactor (docs/REFACTOR/PROGRESS.md): {@link XpCurveRegistry} replaces
 * the single hardcoded {@code DEFAULT_XP_THRESHOLDS} array with per-curve tables, either explicit
 * or formula-derived, resolved once at load time.
 */
public class XpCurveRegistryTest {

    @BeforeEach
    void setUp() {
        // formula curves resolve $curve.level$ through ValmoraAPI -> ScriptModule -> VariableResolver
        // (same chain as CurveVariableProvider in production); read back whatever level the
        // registry attached to that particular evaluation's context.
        ValmoraAPI api = mock(ValmoraAPI.class);
        ScriptModule scriptModule = mock(ScriptModule.class);
        VariableResolver variableResolver = mock(VariableResolver.class);
        ValmoraAPI.setProvider(api);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(variableResolver);
        when(variableResolver.resolve(eq("$curve.level$"), any())).thenAnswer(inv -> {
            ExecutionContext ctx = inv.getArgument(1);
            return ctx.get("curve:level");
        });
    }

    private Valmora mockPlugin(File dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("XpCurveRegistryTest"));
        return plugin;
    }

    @Test
    void freshRegistryAlwaysHasTheBuiltInDefaultCurve() {
        XpCurveRegistry registry = new XpCurveRegistry();
        XpCurve curve = registry.get(XpCurveRegistry.DEFAULT_CURVE_ID);

        // Matches the exact pre-refactor hardcoded thresholds.
        assertEquals(10, curve.getXpForLevel(1));
        assertEquals(20, curve.getXpForLevel(2));
        assertEquals(0, curve.getLevelFromXp(9.9));
        assertEquals(1, curve.getLevelFromXp(10));
    }

    @Test
    void unknownCurveIdFallsBackToDefault() {
        XpCurveRegistry registry = new XpCurveRegistry();
        assertSame(registry.get(XpCurveRegistry.DEFAULT_CURVE_ID), registry.get("nonexistent"));
        assertSame(registry.get(XpCurveRegistry.DEFAULT_CURVE_ID), registry.get(null));
    }

    @Test
    void explicitThresholdsCurveIsLoadedVerbatim() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File skillsDir = new File(dataFolder, "skills");
        skillsDir.mkdirs();
        Files.writeString(new File(skillsDir, "xp_curves.yml").toPath(),
                "xp_curves:\n  slow:\n    thresholds: [100, 300, 700]\n");

        XpCurveRegistry registry = new XpCurveRegistry();
        registry.load(mockPlugin(dataFolder), new ExpressionParser());

        XpCurve curve = registry.get("slow");
        assertEquals(3, curve.getMaxLevel());
        assertEquals(100, curve.getXpForLevel(1));
        assertEquals(300, curve.getXpForLevel(2));
        assertEquals(700, curve.getXpForLevel(3));
    }

    @Test
    void formulaCurveIsPrecomputedOncePerLevelAtLoadTime() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File skillsDir = new File(dataFolder, "skills");
        skillsDir.mkdirs();
        Files.writeString(new File(skillsDir, "xp_curves.yml").toPath(),
                "xp_curves:\n  fast:\n    formula: \"100 * $curve.level$\"\n    max-level: 5\n");

        XpCurveRegistry registry = new XpCurveRegistry();
        registry.load(mockPlugin(dataFolder), new ExpressionParser());

        XpCurve curve = registry.get("fast");
        assertEquals(5, curve.getMaxLevel());
        assertEquals(100, curve.getXpForLevel(1));
        assertEquals(500, curve.getXpForLevel(5));
    }

    @Test
    void loadAlwaysRestoresTheDefaultCurveFirst() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "skills").mkdirs();

        XpCurveRegistry registry = new XpCurveRegistry();
        registry.load(mockPlugin(dataFolder), new ExpressionParser()); // no xp_curves.yml present

        assertEquals(10, registry.get(XpCurveRegistry.DEFAULT_CURVE_ID).getXpForLevel(1));
    }

    @Test
    void clearResetsToBuiltInOnlyState() {
        XpCurveRegistry registry = new XpCurveRegistry();
        registry.clear();
        assertEquals(10, registry.get(XpCurveRegistry.DEFAULT_CURVE_ID).getXpForLevel(1));
    }
}
