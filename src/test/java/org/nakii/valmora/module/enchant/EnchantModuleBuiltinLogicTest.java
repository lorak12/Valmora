package org.nakii.valmora.module.enchant;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the Phase 4 Task 16 follow-up (docs/REFACTOR/PROGRESS.md): {@code valmora:growth},
 * {@code valmora:fortune}, {@code valmora:efficiency}, and {@code valmora:sharpness} used to be
 * separate hardcoded Java classes (deleted). They're now factories over the generic
 * {@code StatBonusLogic}/{@code DamageMultiplierLogic}, and must reproduce the exact old hardcoded
 * per-level values when an enchant YAML gives them no {@code logic-params}.
 */
public class EnchantModuleBuiltinLogicTest {

    private ValmoraAPI api;
    private SystemStats systemStats;

    @BeforeEach
    void setUp() {
        api = mock(ValmoraAPI.class);
        systemStats = mock(SystemStats.class);
        ValmoraAPI.setProvider(api);
        when(api.getSystemStats()).thenReturn(systemStats);
        when(systemStats.getHealth()).thenReturn("health");
        when(systemStats.getMiningFortune()).thenReturn("mining_fortune");
        when(systemStats.getMiningSpeed()).thenReturn("mining_speed");
    }

    private EnchantModule newModuleWithEnchant(String enchantYaml) throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File enchantsDir = new File(dataFolder, "enchants");
        enchantsDir.mkdirs();
        Files.writeString(new File(enchantsDir, "test.yml").toPath(), enchantYaml);

        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EnchantModuleBuiltinLogicTest"));

        // Phase 2/3 of the enchant overhaul: onEnable() now registers enchant variable providers,
        // an EntityDeathEvent listener, and a state-cleanup task through the script module/Bukkit
        // server — none of that is exercised by these builtin-logic tests, so bare mocks are enough.
        ScriptModule scriptModule = mock(ScriptModule.class);
        when(plugin.getScriptModule()).thenReturn(scriptModule);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);

        EnchantModule module = new EnchantModule(plugin);
        module.onEnable();
        return module;
    }

    @Test
    void fortuneWithNoParamsMatchesTheOldHardcodedTenPerLevel() throws IOException {
        EnchantModule module = newModuleWithEnchant("test_fortune:\n  name: Fortune\n  logic: valmora:fortune\n");

        EnchantmentDefinition def = module.getRegistry().get("test_fortune").orElseThrow();
        Player player = mock(Player.class);
        StatManager statManager = mock(StatManager.class);

        def.getLogic().applyStats(player, 3, statManager);

        verify(statManager).addModifier("mining_fortune", 30.0); // 10.0 * level 3
    }

    @Test
    void growthWithNoParamsMatchesTheOldHardcodedTenPerLevel() throws IOException {
        EnchantModule module = newModuleWithEnchant("test_growth:\n  name: Growth\n  logic: valmora:growth\n");

        EnchantmentDefinition def = module.getRegistry().get("test_growth").orElseThrow();
        Player player = mock(Player.class);
        StatManager statManager = mock(StatManager.class);

        def.getLogic().applyStats(player, 2, statManager);

        verify(statManager).addModifier("health", 20.0); // 10.0 * level 2
    }

    @Test
    void efficiencyWithNoParamsMatchesTheOldHardcodedFiftyPerLevel() throws IOException {
        EnchantModule module = newModuleWithEnchant("test_efficiency:\n  name: Efficiency\n  logic: valmora:efficiency\n");

        EnchantmentDefinition def = module.getRegistry().get("test_efficiency").orElseThrow();
        Player player = mock(Player.class);
        StatManager statManager = mock(StatManager.class);

        def.getLogic().applyStats(player, 4, statManager);

        verify(statManager).addModifier("mining_speed", 200.0); // 50.0 * level 4
    }

    @Test
    void fortuneWithCustomParamsOverridesTheDefault() throws IOException {
        EnchantModule module = newModuleWithEnchant(
                "test_fortune_custom:\n  name: Fortune\n  logic: valmora:fortune\n  logic-params:\n    per-level: 25.0\n");

        EnchantmentDefinition def = module.getRegistry().get("test_fortune_custom").orElseThrow();
        StatManager statManager = mock(StatManager.class);

        def.getLogic().applyStats(mock(Player.class), 2, statManager);

        verify(statManager).addModifier("mining_fortune", 50.0); // overridden 25.0 * level 2
    }

    @Test
    void sharpnessWithNoParamsMatchesTheOldHardcodedFivePercentMeleePerLevel() throws IOException {
        EnchantModule module = newModuleWithEnchant("test_sharpness:\n  name: Sharpness\n  logic: valmora:sharpness\n");

        EnchantmentDefinition def = module.getRegistry().get("test_sharpness").orElseThrow();
        var context = new org.nakii.valmora.module.combat.DamageModifierContext(
                10.0, 0.0, 0.0, 0.0, 0.0, org.nakii.valmora.module.combat.DamageType.MELEE);

        def.getLogic().modifyAttack(context, null, null, 5);

        // 1.0 + (5.0/100 * 5) = 1.25 — identical to the old hardcoded `1.0 + 0.05 * level`.
        assertEquals(1.25, context.getDamageMultiplier(), 1e-9);
    }
}
