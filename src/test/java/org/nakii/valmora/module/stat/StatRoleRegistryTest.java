package org.nakii.valmora.module.stat;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 3.1 of the refactor (docs/REFACTOR/PROGRESS.md): {@link StatRoleRegistry} replaces
 * the 15 fixed fields in {@link SystemStats} with a dynamic role->stat-id mapping, while preserving
 * both the original built-in defaults and the legacy {@code config.yml} override path.
 */
public class StatRoleRegistryTest {

    private Valmora mockPlugin(File dataFolder, org.bukkit.configuration.file.FileConfiguration config) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StatRoleRegistryTest"));
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    @Test
    void builtInDefaultsCoverAllFifteenOriginalRoles() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "stats").mkdirs();
        var config = new org.bukkit.configuration.file.YamlConfiguration();

        StatRoleRegistry registry = new StatRoleRegistry();
        registry.load(mockPlugin(dataFolder, config));

        for (String role : new String[]{"health", "mana", "damage", "strength", "defense",
                "crit_chance", "crit_damage", "speed", "health_regen", "mana_regen", "luck",
                "mining_fortune", "mining_speed", "breaking_power", "mining_spread"}) {
            assertEquals(role, registry.get(role), "role '" + role + "' should default to identity mapping");
            assertTrue(registry.has(role));
        }
    }

    @Test
    void unknownRoleResolvesToItselfRatherThanNull() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "stats").mkdirs();
        StatRoleRegistry registry = new StatRoleRegistry();
        registry.load(mockPlugin(dataFolder, new org.bukkit.configuration.file.YamlConfiguration()));

        assertEquals("custom_role", registry.get("custom_role"));
        assertFalse(registry.has("custom_role"));
    }

    @Test
    void legacyConfigYmlOverrideTakesPrecedenceOverBuiltInDefault() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "stats").mkdirs();
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("combat.damage-stat", "custom_damage_stat");

        StatRoleRegistry registry = new StatRoleRegistry();
        registry.load(mockPlugin(dataFolder, config));

        assertEquals("custom_damage_stat", registry.get("damage"));
        // Untouched roles are unaffected by the override.
        assertEquals("defense", registry.get("defense"));
    }

    @Test
    void newRoleFromStatsCoreYamlIsRegisteredWithoutAnyCodeChange() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File statsDir = new File(dataFolder, "stats");
        statsDir.mkdirs();
        Files.writeString(new File(statsDir, "core.yml").toPath(),
                "stat_roles:\n  mana_shield: intelligence\n");

        StatRoleRegistry registry = new StatRoleRegistry();
        registry.load(mockPlugin(dataFolder, new org.bukkit.configuration.file.YamlConfiguration()));

        assertEquals("intelligence", registry.get("mana_shield"));
        assertTrue(registry.has("mana_shield"));
        // Original 15 roles are still present alongside the new one.
        assertEquals("health", registry.get("health"));
    }

    @Test
    void statsCoreYamlOverridesBuiltInDefaultTooWhenBothArePresent() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        File statsDir = new File(dataFolder, "stats");
        statsDir.mkdirs();
        Files.writeString(new File(statsDir, "core.yml").toPath(),
                "stat_roles:\n  damage: custom_offense_stat\n");

        StatRoleRegistry registry = new StatRoleRegistry();
        registry.load(mockPlugin(dataFolder, new org.bukkit.configuration.file.YamlConfiguration()));

        assertEquals("custom_offense_stat", registry.get("damage"));
    }

    @Test
    void clearRemovesEverythingIncludingBuiltInDefaults() throws IOException {
        File dataFolder = Files.createTempDirectory("valmora-test").toFile();
        new File(dataFolder, "stats").mkdirs();
        StatRoleRegistry registry = new StatRoleRegistry();
        registry.load(mockPlugin(dataFolder, new org.bukkit.configuration.file.YamlConfiguration()));

        registry.clear();

        assertFalse(registry.has("health"));
        assertTrue(registry.roleNames().isEmpty());
    }
}
