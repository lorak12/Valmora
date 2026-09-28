package org.nakii.valmora.module.script.pipeline;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadReport;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.module.combat.CombatFlows;
import org.nakii.valmora.module.death.DeathFlows;
import org.nakii.valmora.module.fishing.flow.FishingFlows;
import org.nakii.valmora.module.item.AbilityFlows;
import org.nakii.valmora.module.mob.MobFlows;
import org.nakii.valmora.module.resource.ResourceFlows;
import org.nakii.valmora.module.script.ScriptModule;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Every shipped {@code *_pipeline.yml} compiles cleanly against its domain — no errors, no
 * warnings, every declared flow installed from the file itself. A typo'd built-in, param or anchor
 * in a default file fails here instead of on a server.
 */
class BundledPipelinesTest {

    private Valmora plugin;
    private ScriptModule scriptModule;
    private File dataFolder;
    private MockedStatic<Valmora> valmoraStatic;
    private MockedStatic<ValmoraAPI> apiStatic;

    static Stream<FlowDomain> domains() {
        return Stream.of(FishingFlows.DOMAIN, ResourceFlows.DOMAIN, DeathFlows.DOMAIN,
                AbilityFlows.DOMAIN, MobFlows.DOMAIN, CombatFlows.DOMAIN);
    }

    @BeforeEach
    void setUp() throws Exception {
        dataFolder = Files.createTempDirectory("valmora-bundled").toFile();
        plugin = mock(Valmora.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("BundledPipelinesTest"));
        valmoraStatic = mockStatic(Valmora.class);
        valmoraStatic.when(Valmora::getInstance).thenReturn(plugin);
        scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);
        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(api.getHookBus()).thenReturn(scriptModule.getHookBus());
        apiStatic = mockStatic(ValmoraAPI.class);
        apiStatic.when(ValmoraAPI::getInstance).thenReturn(api);
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    @ParameterizedTest
    @MethodSource("domains")
    void shippedFileLoadsCleanly(FlowDomain domain) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + domain.fileName())) {
            assertNotNull(in, domain.fileName() + " must be shipped");
            Files.copy(in, new File(dataFolder, domain.fileName()).toPath());
        }
        for (FlowSpec<?> spec : domain.flows()) spec.install(null);
        LoadReport.global().drain();

        FlowYamlLoader.load(plugin, scriptModule, domain);

        List<ConfigDiagnostic> problems = LoadReport.global().current().stream()
                .filter(d -> d.severity() == Severity.ERROR || d.severity() == Severity.WARN)
                .toList();
        assertTrue(problems.isEmpty(), () -> domain.fileName() + ": " + problems);
        for (FlowSpec<?> spec : domain.flows()) {
            assertNotNull(spec.current(), spec.key() + " should be loaded");
            assertEquals("file", spec.current().source(), spec.key() + " should come from the file itself");
            assertFalse(spec.current().steps().isEmpty(), spec.key() + " should have steps");
        }
    }

    @ParameterizedTest
    @MethodSource("domains")
    void everyBuiltinIsUsedByTheDefaultFlow(FlowDomain domain) throws Exception {
        for (FlowSpec<?> spec : domain.flows()) spec.install(null);
        FlowYamlLoader.load(plugin, scriptModule, domain); // no file → bundled
        for (FlowSpec<?> spec : domain.flows()) {
            var used = spec.current().steps().stream().map(s -> s.kind()).toList();
            for (var factory : spec.builtins()) {
                // The generic feedback steps are optional extras (e.g. the deny cue has no particle).
                if (factory.id().equals("play_sound") || factory.id().equals("spawn_particle")) continue;
                assertTrue(used.contains("builtin:" + factory.id()),
                        spec.key() + ": built-in '" + factory.id() + "' isn't in the default flow — the default file should list every step");
            }
        }
    }
}
