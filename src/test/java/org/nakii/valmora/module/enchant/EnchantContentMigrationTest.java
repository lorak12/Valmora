package org.nakii.valmora.module.enchant;

import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.script.ScriptModule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Loads the actual shipped {@code enchants/example_enchantments.yml} (Phase 4's content migration)
 * through the real {@link ScriptModule} parser stack (conditions/events/expressions) — not a mock —
 * so a typo'd condition/formula/trigger name in that file fails this test the same way it would
 * fail to load on a real server, rather than being silently swallowed by a hand-written test YAML
 * that never touches the parsers the shipped file actually exercises.
 *
 * <p>Runtime behavior for the specific mechanics used here (combo counters, per-attacker stacking,
 * combat modifiers, the {@code heal} event) is already covered end-to-end by
 * {@code EnchantDispatcherTest}/{@code EnchantCombatHookTest}/{@code TransientStateTrackerTest}/
 * {@code HealEventFactoryTest} from Phases 2-3 — this test only spot-checks that the shipped YAML's
 * structure compiles into what those mechanics expect.
 */
class EnchantContentMigrationTest {

    private EnchantmentRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File enchantsDir = new File(dataFolder, "enchants");
        enchantsDir.mkdirs();
        try (InputStream in = getClass().getResourceAsStream("/enchants/example_enchantments.yml")) {
            assertNotNull(in, "shipped example_enchantments.yml must be on the test classpath");
            Files.copy(in, new File(enchantsDir, "example_enchantments.yml").toPath());
        }

        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EnchantContentMigrationTest"));

        // A real ScriptModule (not a mock) — the migrated YAML actually exercises its
        // condition/event/expression parsers, unlike EnchantModuleBuiltinLogicTest's bare-mock
        // fixture (which never has a combat:/triggers:/stats: section to parse).
        ScriptModule scriptModule = new ScriptModule(plugin);
        scriptModule.onEnable();
        when(plugin.getScriptModule()).thenReturn(scriptModule);

        org.bukkit.Server server = mock(org.bukkit.Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);

        EnchantModule module = new EnchantModule(plugin);
        module.onEnable();
        registry = module.getRegistry();
    }

    @AfterEach
    void tearDown() {
        // ScriptModule.onEnable() registers into a shared HookBus instance per module instance
        // here (not the real Valmora singleton), so nothing needs unregistering across tests.
    }

    @Test
    void everyShippedEnchantParsesSuccessfully() {
        Set<String> expected = Set.of("sharpness", "growth", "execute", "first_strike", "life_steal",
                "lethality", "protection", "respite", "thorns", "fortune", "efficiency");
        assertEquals(expected, registry.getKeys());
    }

    @Test
    void firstStrikeCompilesItsCombatComboAndStateBlocks() {
        EnchantmentDefinition def = registry.get("first_strike").orElseThrow();
        assertNotNull(def.getModifyAttack(), "combat.modify-attack must compile");
        assertTrue(def.getTriggers().containsKey(EnchantTrigger.ON_ATTACK_POST));
        assertTrue(def.getTransientStates().containsKey("combo_counter"));
        assertEquals(3, def.getTransientStates().get("combo_counter").maxStacks());
        assertTrue(def.getTransientStates().get("combo_counter").resetOnTargetSwitch());
        assertEquals("1.0 + (0.25 * $level$)", def.getVariables().get("bonus_multiplier"));
    }

    @Test
    void lethalityCompilesItsPerAttackerStackingBlocks() {
        EnchantmentDefinition def = registry.get("lethality").orElseThrow();
        assertNotNull(def.getModifyAttack());
        assertTrue(def.getTriggers().containsKey(EnchantTrigger.ON_ATTACK_POST));
        assertTrue(def.getTransientStates().containsKey("stacks"));
        assertEquals(4, def.getTransientStates().get("stacks").maxStacks());
        assertEquals(4, def.getTransientStates().get("stacks").resetAfterSeconds());
    }

    @Test
    void growthCompilesAPlainStatBonus() {
        EnchantmentDefinition def = registry.get("growth").orElseThrow();
        assertTrue(def.getStatBonuses().containsKey("health"));
    }

    @Test
    void respiteCompilesATernaryStatBonus() {
        EnchantmentDefinition def = registry.get("respite").orElseThrow();
        assertTrue(def.getStatBonuses().containsKey("health_regen"));
    }

    @Test
    void thornsIsDeliberatelyLeftOnLegacyLogicUnmigrated() {
        EnchantmentDefinition def = registry.get("thorns").orElseThrow();
        assertNotNull(def.getLogic(), "thorns must still resolve valmora:thorns");
        assertTrue(def.getTriggers().isEmpty());
        assertNull(def.getModifyDefend());
    }

    @Test
    void lifeStealCompilesItsHealTrigger() {
        EnchantmentDefinition def = registry.get("life_steal").orElseThrow();
        assertTrue(def.getTriggers().containsKey(EnchantTrigger.ON_ATTACK_POST));
    }
}
