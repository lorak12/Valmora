package org.nakii.valmora.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V1_RELEASE_CHECKLIST.md §2 ("Config & data validation (automatable)") — unlike
 * {@link YamlConfigLoadTest}, which only exercises a hand-maintained subset of fixture files, this
 * walks the *actual* shipped {@code src/main/resources} tree so a new file can't silently ship
 * with a YAML syntax error, a duplicate ID that would clobber an earlier registration, or an
 * orphaned machine/GUI pairing.
 *
 * <p>Resolved relative to the working directory, which for a Gradle {@code test} task is the
 * project root by default — matching every other file-path assumption already made across this
 * suite (see {@code PetXpFormulaTest}'s temp-dir pattern for the alternative used where the
 * working-dir assumption can't be relied on). Guarded with {@code @EnabledIf} rather than a hard
 * failure so a differently-configured runner skips cleanly instead of reporting a false negative.
 */
@Tag("config")
class ResourceIntegrityTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    static boolean resourcesDirPresent() {
        return Files.isDirectory(RESOURCES);
    }

    private static List<Path> ymlFilesUnder(String subDir) throws IOException {
        Path dir = RESOURCES.resolve(subDir);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml"))
                    .collect(Collectors.toList());
        }
    }

    private static YamlConfiguration load(Path path) {
        return YamlConfiguration.loadConfiguration(path.toFile());
    }

    // --- Full-tree parse sweep --------------------------------------------------------------

    @TestFactory
    @EnabledIf("resourcesDirPresent")
    Stream<DynamicTest> everyShippedYamlFileParsesWithoutException() throws IOException {
        List<Path> all;
        try (Stream<Path> stream = Files.walk(RESOURCES)) {
            all = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml"))
                    .collect(Collectors.toList());
        }
        assertFalse(all.isEmpty(), "expected to find shipped .yml files under " + RESOURCES);
        return all.stream().map(path -> DynamicTest.dynamicTest(
                RESOURCES.relativize(path).toString(),
                () -> assertDoesNotThrow(() -> load(path), "Failed to parse: " + path)));
    }

    // --- Duplicate top-level ID detection ----------------------------------------------------
    // Every module here registers definitions keyed by top-level YAML key into one flat registry
    // per type; a second file reusing the same key wins silently (last-loaded), per YamlLoader's
    // simple overwrite-on-put behavior. That's exactly the kind of bug this sweep should catch
    // before it reaches a server as "why did my item turn into a different item after an update".

    private void assertNoDuplicateTopLevelKeys(String subDir) throws IOException {
        Map<String, List<String>> owners = new HashMap<>();
        for (Path file : ymlFilesUnder(subDir)) {
            YamlConfiguration cfg = load(file);
            String relative = RESOURCES.relativize(file).toString();
            for (String key : cfg.getKeys(false)) {
                owners.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(relative);
            }
        }
        List<String> dupes = owners.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + e.getValue())
                .collect(Collectors.toList());
        assertTrue(dupes.isEmpty(), "Duplicate " + subDir + " ID(s) across files (last-loaded silently wins):\n"
                + String.join("\n", dupes));
    }

    @Test
    @EnabledIf("resourcesDirPresent")
    void noDuplicateItemIdsAcrossItemFiles() throws IOException {
        assertNoDuplicateTopLevelKeys("items");
    }

    @Test
    @EnabledIf("resourcesDirPresent")
    void noDuplicateMobIdsAcrossMobFiles() throws IOException {
        assertNoDuplicateTopLevelKeys("mobs");
    }

    @Test
    @EnabledIf("resourcesDirPresent")
    void noDuplicateGuiIdsAcrossGuiFiles() throws IOException {
        assertNoDuplicateTopLevelKeys("guis");
    }

    @Test
    @EnabledIf("resourcesDirPresent")
    void noDuplicateRecipeIdsAcrossRecipeFiles() throws IOException {
        // Walked recursively (ymlFilesUnder), so this also covers CLAUDE.md §9.3's claimed
        // recipes/<subfolder>/ organisation once/if any such subfolder ships.
        assertNoDuplicateTopLevelKeys("recipes");
    }

    // --- Cross-reference: GUI <-> recipe machine pairing -------------------------------------

    /**
     * Machine IDs fully owned by a {@code DynamicMachineHandler} registered in Java (checked
     * before YAML recipes per CLAUDE.md §8.5/§9) — these legitimately have zero YAML recipes.
     * Kept in sync by hand; grep {@code registerHandler(} across src/main/java if this list ever
     * looks stale (found: AlchemyModule, EnchantModule, ReforgeModule x2, RecipeModule#anvil).
     */
    private static final Set<String> HANDLER_OWNED_MACHINES = Set.of(
            "alchemy", "anvil", "enchanting_table", "forge_random", "reforge_anvil");

    @Test
    @EnabledIf("resourcesDirPresent")
    void everyGuiMachineHasAtLeastOneMatchingRecipeOrARegisteredHandler() throws IOException {
        Set<String> recipeMachines = new HashSet<>();
        for (Path file : ymlFilesUnder("recipes")) {
            YamlConfiguration cfg = load(file);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section != null && section.contains("machine")) {
                    recipeMachines.add(section.getString("machine").toLowerCase());
                }
            }
        }

        List<String> orphans = new ArrayList<>();
        for (Path file : ymlFilesUnder("guis")) {
            YamlConfiguration cfg = load(file);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section == null || !section.contains("machine")) continue;
                String machine = section.getString("machine").toLowerCase();
                if (!recipeMachines.contains(machine) && !HANDLER_OWNED_MACHINES.contains(machine)) {
                    orphans.add(RESOURCES.relativize(file) + " → " + key + " (machine: " + machine + ")");
                }
            }
        }
        assertTrue(orphans.isEmpty(), "GUI(s) declare a machine with no recipe referencing it — either a "
                + "DynamicMachineHandler covers it (fine, but worth a comment) or it's dead content:\n"
                + String.join("\n", orphans));
    }

    // --- Cross-reference: every recipe/loot-table `item:` resolves to something real ---------

    private boolean resolves(String itemId, Set<String> knownItemIds) {
        if (itemId == null) return false;
        if (knownItemIds.contains(itemId.toLowerCase())) return true;
        return Material.matchMaterial(itemId.toUpperCase()) != null;
    }

    private Set<String> knownItemIds() throws IOException {
        Set<String> ids = new HashSet<>();
        for (Path file : ymlFilesUnder("items")) {
            for (String key : load(file).getKeys(false)) {
                ids.add(key.toLowerCase());
            }
        }
        return ids;
    }

    /** Pulls every {@code item:} value out of one {@code inputs:}/{@code outputs:} section,
     *  which is shaped either as a map (SHAPED/EXACT_SLOT keys, or named outputs) or a list
     *  (SHAPELESS) of {@code {item, amount}} entries — see CLAUDE.md §9.2. */
    private List<String> extractItemRefs(Object inputsOrOutputs) {
        List<String> items = new ArrayList<>();
        if (inputsOrOutputs instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof Map<?, ?> entry && entry.get("item") != null) {
                    items.add(entry.get("item").toString());
                }
            }
        } else if (inputsOrOutputs instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> entry && entry.get("item") != null) {
                    items.add(entry.get("item").toString());
                }
            }
        }
        return items;
    }

    @Test
    @EnabledIf("resourcesDirPresent")
    void everyRecipeItemReferenceResolvesToAKnownItemOrVanillaMaterial() throws IOException {
        Set<String> knownItemIds = knownItemIds();
        List<String> violations = new ArrayList<>();

        for (Path file : ymlFilesUnder("recipes")) {
            YamlConfiguration cfg = load(file);
            for (String recipeKey : cfg.getKeys(false)) {
                ConfigurationSection section = cfg.getConfigurationSection(recipeKey);
                if (section == null) continue;
                List<String> refs = new ArrayList<>();
                refs.addAll(extractItemRefs(section.get("inputs")));
                refs.addAll(extractItemRefs(section.get("outputs")));
                for (String ref : refs) {
                    if (!resolves(ref, knownItemIds)) {
                        violations.add(RESOURCES.relativize(file) + " → " + recipeKey + " references unknown item '" + ref + "'");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    @EnabledIf("resourcesDirPresent")
    void everyMobLootTableItemReferenceResolvesToAKnownItemOrVanillaMaterial() throws IOException {
        Set<String> knownItemIds = knownItemIds();
        List<String> violations = new ArrayList<>();

        for (Path file : ymlFilesUnder("mobs")) {
            YamlConfiguration cfg = load(file);
            for (String mobKey : cfg.getKeys(false)) {
                List<?> drops = cfg.getMapList(mobKey + ".loot-table.drops");
                for (Object raw : drops) {
                    if (!(raw instanceof Map<?, ?> drop)) continue;
                    Object item = drop.get("item");
                    if (item != null && !resolves(item.toString(), knownItemIds)) {
                        violations.add(RESOURCES.relativize(file) + " → " + mobKey
                                + " loot-table references unknown item '" + item + "'");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    // --- Ability mechanic name sweep, extended to every item file ---------------------------

    @Test
    @EnabledIf("resourcesDirPresent")
    void everyItemAbilityMechanicAcrossAllItemFilesIsKnownActive() throws IOException {
        // Kept in sync with AbilityManager#registerMechanics() — see the equivalent, narrower
        // check in YamlConfigLoadTest#testNewItemsFile_abilityMechanicTypesAreAllKnownActive for
        // the file-by-file rationale. This variant sweeps the whole items/ tree instead of a
        // hand-picked file list, so a new item file can't slip a typo'd mechanic type past review.
        Set<String> knownActiveMechanics = Set.of(
                "DAMAGE", "HEAL", "APPLY_EFFECT", "MODIFY_STAT", "TELEPORT",
                "PUSH_ENTITIES", "PULL_ENTITIES", "SCRIPT",
                "LAUNCH_PROJECTILE", "LAUNCH_PLAYER", "GIVE_COINS", "TAKE_COINS",
                "CANCEL_TRAMPLE", "CHARGE_JUMP", "IGNITE", "AOE_MINE",
                // Registered by GuiModule#onEnable() into the shared MechanicRegistry, not by
                // AbilityManager#registerMechanics() — easy to miss, which is exactly how the
                // narrower YamlConfigLoadTest sweep (3 hand-picked files) missed backpacks.yml
                // using it. See OpenContainerMechanic / GuiModule.java:98.
                "OPEN_CONTAINER_GUI");

        List<String> violations = new ArrayList<>();
        for (Path file : ymlFilesUnder("items")) {
            YamlConfiguration cfg = load(file);
            for (String itemKey : cfg.getKeys(false)) {
                ConfigurationSection abilities = cfg.getConfigurationSection(itemKey + ".abilities");
                if (abilities == null) continue;
                for (String abilityKey : abilities.getKeys(false)) {
                    List<?> mechanics = abilities.getList(abilityKey + ".mechanics");
                    if (mechanics == null) continue;
                    for (Object raw : mechanics) {
                        if (!(raw instanceof Map<?, ?> map)) continue;
                        Object type = map.get("type");
                        if (type != null && !knownActiveMechanics.contains(type.toString())) {
                            violations.add(RESOURCES.relativize(file) + " → " + itemKey + "." + abilityKey
                                    + " uses unknown/inactive mechanic type '" + type + "'");
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
