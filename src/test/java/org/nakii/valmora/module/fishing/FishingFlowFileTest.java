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
import org.nakii.valmora.api.pipeline.flow.CompiledFlow;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadReport;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.module.fishing.flow.FishingFlows;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.ItemRegistry;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.zone.ZoneManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * What an admin can do to {@code fishing_pipeline.yml}'s flows, and how bad edits are handled
 * (docs/PIPELINE_FLOWS_PLAN.md §3.1, §4, §6 step 7). Fishing is the vehicle; the rules are
 * {@code FlowYamlLoader}'s and apply to every pipeline file.
 */
class FishingFlowFileTest {

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
    private ItemStack cod;

    @BeforeEach
    void setUp() throws Exception {
        FishingFlows.CATCH.install(null);
        FishingFlows.BITE.install(null);
        LoadReport.global().drain();

        dataFolder = Files.createTempDirectory("valmora-fishing-file").toFile();
        plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FishingFlowFileTest"));
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
        cod = mock(ItemStack.class);
        when(itemRegistry.createItemStack("cod")).thenReturn(Optional.of(cod));
        manager.getRegistry().register("default", new FishingLootTable("default",
                List.of(new FishingLootEntry("cod", 1, 1, 1)), 0.0, null));
    }

    @AfterEach
    void tearDown() {
        valmoraStatic.close();
        apiStatic.close();
    }

    private void load(String yaml) throws Exception {
        if (yaml != null) {
            Files.writeString(new File(dataFolder, "fishing_pipeline.yml").toPath(), yaml, StandardCharsets.UTF_8);
        }
        LoadReport.global().drain();
        new FishingPipelineLoader(plugin, scriptModule).load();
    }

    private List<ConfigDiagnostic> problems(Severity severity) {
        return LoadReport.global().current().stream().filter(d -> d.severity() == severity).toList();
    }

    private static final String DEFAULT_CATCH = """
            version: 2
            flows:
              catch:
                - builtin: resolve_table
                - anchor: pre_catch
                - builtin: roll_sea_creature
                - builtin: spawn_sea_creature
                - builtin: roll_loot
            %s
                - anchor: post_catch
            """;

    /** The default catch flow with {@code giveAndExtras} (written unindented) in place of give_catch. */
    private static String catchFlow(String giveAndExtras) {
        return DEFAULT_CATCH.formatted(giveAndExtras.stripIndent().indent(4).stripTrailing());
    }

    @Test
    void missingFile_usesTheBundledDefault() throws Exception {
        load(null);
        CompiledFlow flow = FishingFlows.CATCH.current();
        assertNotNull(flow);
        assertEquals("bundled", flow.source());
        assertTrue(manager.handleCatch(player, hook));
        verify(inventory).addItem(cod);
        assertTrue(problems(Severity.ERROR).isEmpty(), () -> "bundled file should load cleanly: " + problems(Severity.ERROR));
    }

    @Test
    void legacyStagesOnlyFile_usesBundledFlowsAndStillRunsItsStages() throws Exception {
        load("""
                stages:
                  - id: veto
                    when: pre_catch
                    on-pass:
                      - "interrupt"
                """);
        assertEquals("bundled", FishingFlows.CATCH.current().source());
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
        assertTrue(problems(Severity.INFO).stream().anyMatch(d -> d.message().contains("older stages-only format")));
    }

    @Test
    void disabledStep_isSkipped() throws Exception {
        load(catchFlow("""
                    - builtin: give_catch
                      enabled: false"""));
        assertEquals("file", FishingFlows.CATCH.current().source());
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
    }

    @Test
    void falseCondition_skipsTheStepButTheFlowContinues() throws Exception {
        load(catchFlow("""
                    - builtin: give_catch
                      conditions:
                        - "1 == 2"
                    - id: after
                      on-pass:
                        - "tag add reached_after\""""));
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
    }

    @Test
    void scriptStep_canChangeStateWithPipelineSet() throws Exception {
        load(catchFlow("""
                    - id: triple
                      on-pass:
                        - "pipeline_set fishing.amount $fishing.amount$ * 3"
                    - builtin: give_catch"""));
        assertTrue(problems(Severity.ERROR).isEmpty(), () -> problems(Severity.ERROR).toString());
        assertTrue(manager.handleCatch(player, hook));
        verify(cod).setAmount(3);
    }

    @Test
    void scriptStepInterrupt_stopsTheFlowThere() throws Exception {
        load(catchFlow("""
                    - id: stop_here
                      on-pass:
                        - "interrupt"
                    - builtin: give_catch"""));
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
    }

    @Test
    void reorderedSteps_changeTheOutcome() throws Exception {
        // give_catch before roll_loot has nothing to give yet.
        load("""
                version: 2
                flows:
                  catch:
                    - builtin: resolve_table
                    - builtin: give_catch
                    - builtin: roll_loot
                """);
        assertFalse(manager.handleCatch(player, hook));
        verify(inventory, never()).addItem(any());
    }

    @Test
    void expressionParam_isEvaluatedAgainstTheFlow() throws Exception {
        MobDefinition kraken = mock(MobDefinition.class);
        when(mobManager.getMobDefinition("kraken")).thenReturn(kraken);
        manager.getRegistry().register("default", new FishingLootTable("default",
                List.of(new FishingLootEntry("cod", 1, 1, 1)), 0.0, "kraken"));
        load(catchFlow("""
                    - builtin: give_catch""").replace("- builtin: roll_sea_creature",
                "- builtin: roll_sea_creature\n      params:\n        chance: \"$fishing.sea_creature_chance$ + 1\""));
        assertTrue(problems(Severity.ERROR).isEmpty(), () -> problems(Severity.ERROR).toString());
        assertTrue(manager.handleCatch(player, hook));
        verify(mobManager).spawnMob(kraken, hook);
    }

    @Test
    void unknownBuiltin_onFirstLoad_fallsBackToBundled() throws Exception {
        load(catchFlow("""
                    - builtin: give_catchh"""));
        assertEquals("bundled", FishingFlows.CATCH.current().source());
        assertTrue(problems(Severity.ERROR).stream().anyMatch(d -> d.message().contains("unknown built-in step 'give_catchh'")));
        assertTrue(manager.handleCatch(player, hook));
    }

    @Test
    void brokenFlow_onReload_keepsTheLastGoodOne() throws Exception {
        load(catchFlow("""
                    - builtin: give_catch
                      enabled: false"""));
        CompiledFlow good = FishingFlows.CATCH.current();
        load(catchFlow("""
                    - builtin: no_such_step"""));
        assertSame(good, FishingFlows.CATCH.current());
    }

    @Test
    void missingRequiredStep_failsTheFlow() throws Exception {
        load("""
                version: 2
                flows:
                  catch:
                    - builtin: roll_loot
                    - builtin: give_catch
                """);
        assertEquals("bundled", FishingFlows.CATCH.current().source());
        assertTrue(problems(Severity.ERROR).stream().anyMatch(d -> d.message().contains("required step 'builtin: resolve_table'")));
    }

    @Test
    void duplicateStepIds_failTheFlow() throws Exception {
        load(catchFlow("""
                    - builtin: give_catch
                    - builtin: give_catch"""));
        assertEquals("bundled", FishingFlows.CATCH.current().source());
    }

    @Test
    void newerVersion_isRefused() throws Exception {
        load("""
                version: 3
                flows:
                  catch:
                    - builtin: resolve_table
                """);
        assertEquals("bundled", FishingFlows.CATCH.current().source());
        assertTrue(problems(Severity.ERROR).stream().anyMatch(d -> d.message().contains("newer than this Valmora supports")));
    }

    @Test
    void pipelineSet_unknownKey_isReportedAtLoad() throws Exception {
        load(catchFlow("""
                    - id: bad
                      on-pass:
                        - "pipeline_set fishing.nope 1"
                    - builtin: give_catch"""));
        assertTrue(problems(Severity.ERROR).stream().anyMatch(d -> d.message().contains("pipeline_set: 'fishing.nope'")));
    }

    @Test
    void flowMissingFromTheFile_usesBundledForThatFlowOnly() throws Exception {
        load(catchFlow("""
                    - builtin: give_catch"""));
        assertEquals("file", FishingFlows.CATCH.current().source());
        assertEquals("bundled", FishingFlows.BITE.current().source());
    }

    @Test
    void legacyStageAtARemovedAnchor_isWarnedAbout() throws Exception {
        load("""
                version: 2
                flows:
                  catch:
                    - builtin: resolve_table
                    - builtin: roll_loot
                    - builtin: give_catch
                stages:
                  - id: never
                    when: post_catch
                    on-pass: [ "interrupt" ]
                """);
        assertTrue(problems(Severity.WARN).stream().anyMatch(d -> d.message().contains("it will never run")));
    }
}
