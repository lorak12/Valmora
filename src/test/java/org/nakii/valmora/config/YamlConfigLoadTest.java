package org.nakii.valmora.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("config")
class YamlConfigLoadTest {

    // All YAML resource paths under src/main/resources (excluding plugin.yml)
    static Stream<String> allYamlFiles() {
        return Stream.of(
                "/config.yml",
                "/items/example.yml",
                "/items/alchemy_ingredients.yml",
                "/items/new_items.yml",
                "/mobs/test_mobs.yml",
                "/skills/combat.yml",
                "/skills/mining.yml",
                "/skills/foraging.yml",
                "/skills/fishing.yml",
                "/skills/alchemy.yml",
                "/recipes/forge.yml",
                "/recipes/crafting_table.yml",
                "/recipes/alchemy.yml",
                "/alchemy/effects.yml",
                "/alchemy/modifiers.yml",
                "/alchemy/healing_boost.yml",
                "/enchants/example_enchantments.yml",
                "/fishing/hub_fishing.yml",
                "/guis/stats.yml",
                "/guis/skills_list.yml",
                "/guis/skills_details.yml",
                "/guis/crafting.yml",
                "/guis/forge.yml",
                "/guis/anvil.yml",
                "/guis/enchanting.yml",
                "/guis/active_effects.yml",
                "/guis/bank.yml",
                "/guis/alchemy.yml",
                "/warps/hub.yml",
                "/zones/test_zones.yml",
                "/stats/core.yml",
                "/quests/blacksmith_hub/quest.yml",
                "/quests/blacksmith_hub/quests.yml",
                "/quests/blacksmith_hub/events.yml",
                "/quests/blacksmith_hub/blacksmith.yml",
                "/quests/forgotten_mine/quest.yml",
                "/quests/forgotten_mine/quests.yml",
                "/quests/forgotten_mine/notifications.yml",
                "/quests/forgotten_mine/conversations.yml"
        );
    }

    private YamlConfiguration load(String resourcePath) {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        assertNotNull(in, "Resource not found: " + resourcePath);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allYamlFiles")
    void testAllYamlFilesParseWithoutException(String path) {
        assertDoesNotThrow(() -> load(path), "Failed to parse: " + path);
    }

    @Test
    void testItemFiles_haveMaterialField() {
        for (String path : List.of("/items/example.yml", "/items/alchemy_ingredients.yml",
                "/items/new_items.yml")) {
            YamlConfiguration cfg = load(path);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                assertNotNull(section, path + " / " + key + " is not a section");
                assertTrue(section.contains("material"),
                        path + " → " + key + " is missing 'material' field");
            }
        }
    }

    @Test
    void testMobFiles_haveTypeAndHealth() {
        YamlConfiguration cfg = load("/mobs/test_mobs.yml");
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) continue;
            assertTrue(section.contains("type"),
                    "mobs/test_mobs.yml → " + key + " is missing 'type'");
            assertTrue(section.contains("health"),
                    "mobs/test_mobs.yml → " + key + " is missing 'health'");
        }
    }

    @Test
    void testSkillFiles_haveMaxLevelAndXpCurve() {
        for (String path : List.of("/skills/mining.yml", "/skills/combat.yml",
                "/skills/fishing.yml", "/skills/foraging.yml", "/skills/alchemy.yml")) {
            YamlConfiguration cfg = load(path);
            assertTrue(cfg.contains("max-level"), path + " is missing 'max-level'");
            assertTrue(cfg.contains("xp-curve"), path + " is missing 'xp-curve'");
        }
    }

    @Test
    void testRecipeFiles_eachRecipeHasMachineAndType() {
        for (String path : List.of("/recipes/forge.yml", "/recipes/crafting_table.yml",
                "/recipes/alchemy.yml")) {
            YamlConfiguration cfg = load(path);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section == null) continue; // comment-only section may produce null
                assertTrue(section.contains("machine"),
                        path + " → " + key + " is missing 'machine'");
                assertTrue(section.contains("type"),
                        path + " → " + key + " is missing 'type'");
            }
        }
    }

    @Test
    void testRecipeTypes_areValidValues() {
        Set<String> validTypes = Set.of("EXACT_SLOT", "SHAPED", "SHAPELESS");
        for (String path : List.of("/recipes/forge.yml", "/recipes/crafting_table.yml",
                "/recipes/alchemy.yml")) {
            YamlConfiguration cfg = load(path);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section == null) continue;
                String type = section.getString("type");
                if (type != null) {
                    assertTrue(validTypes.contains(type.toUpperCase()),
                            path + " → " + key + " has invalid type: " + type);
                }
            }
        }
    }

    @Test
    void testGuiFiles_haveRowsField() {
        // GUI files define rows under a named root key, e.g. "stats: { rows: 6 }"
        for (String path : List.of("/guis/stats.yml", "/guis/crafting.yml",
                "/guis/alchemy.yml", "/guis/forge.yml")) {
            YamlConfiguration cfg = load(path);
            boolean found = false;
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section != null && section.contains("rows")) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, path + " has no section containing 'rows'");
        }
    }

    @Test
    void testStatsCoreFile_hasStatDefinitions() {
        YamlConfiguration cfg = load("/stats/core.yml");
        assertFalse(cfg.getKeys(false).isEmpty(), "stats/core.yml has no stat definitions");
        // Each stat must have display-name
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) continue;
            assertTrue(section.contains("display-name") || section.contains("displayName"),
                    "stats/core.yml → " + key + " is missing display-name");
        }
    }

    @Test
    void testAlchemyEffectsFile_hasEffectsWithTiersAndDuration() {
        for (String path : List.of("/alchemy/effects.yml", "/alchemy/healing_boost.yml")) {
            YamlConfiguration cfg = load(path);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section == null) continue;
                assertTrue(section.contains("tiers") || section.contains("duration"),
                        path + " → " + key + " missing 'tiers' or 'duration'");
            }
        }
    }

    @Test
    void testItemRarityValues_whenPresent_areKnownValues() {
        Set<String> knownRarities = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY");
        for (String path : List.of("/items/example.yml", "/items/alchemy_ingredients.yml")) {
            YamlConfiguration cfg = load(path);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section == null || !section.contains("rarity")) continue;
                String rarity = section.getString("rarity", "").toUpperCase();
                assertTrue(knownRarities.contains(rarity),
                        path + " → " + key + " has unknown rarity: " + rarity);
            }
        }
    }
}
