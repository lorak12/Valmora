package org.nakii.valmora.module.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.infrastructure.config.refs.Kinds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeDefinitionParser {

    /**
     * Every smithing recipe this plugin registered with Bukkit. RecipeModule removes them all on
     * disable, so a smithing recipe deleted from YAML is actually gone after a reload (it used to
     * stay registered until restart, since removal only ran for ids that still existed).
     */
    static final java.util.Set<org.bukkit.NamespacedKey> REGISTERED_SMITHING_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Unregisters every smithing recipe registered through this parser. */
    public static void unregisterSmithingRecipes(org.bukkit.Server server) {
        for (org.bukkit.NamespacedKey key : REGISTERED_SMITHING_KEYS) server.removeRecipe(key);
        REGISTERED_SMITHING_KEYS.clear();
    }

    private final Valmora plugin;

    public RecipeDefinitionParser(Valmora plugin) {
        this.plugin = plugin;
    }

    public LoadResult<RecipeDefinition, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            // SMITHING bypasses the GUI-based match()/consume() engine entirely — it registers a
            // real vanilla SmithingTransformRecipe so the actual smithing table block UI works
            // (CLAUDE.md §14.16), which is unrelated to the machine-GUI RecipeType state machine
            // below. Previously not wired in at all.
            if ("SMITHING".equalsIgnoreCase(section.getString("type", ""))) {
                return parseSmithing(id, section, filePath);
            }
            // recipes/anvil/*.yml entries (type: UPGRADE/TRANSMUTE) are parsed by AnvilRecipeParser
            // instead (RecipeModule.loadAnvilRecipes) — this generic loader also recursively scans
            // that subfolder (YamlLoader.load scans all subfolders), so skip them silently here
            // rather than failing on an unrecognized RecipeType.
            String rawType = section.getString("type", "");
            if ("UPGRADE".equalsIgnoreCase(rawType) || "TRANSMUTE".equalsIgnoreCase(rawType)) {
                return LoadResult.success(new RecipeDefinition(id, null, RecipeType.EXACT_SLOT, Map.of(), List.of(), List.of(), null));
            }

            String machine = section.getString("machine");
            ConfigReader reader = ConfigReader.of(section).knownKeys(KNOWN_KEYS);
            if (machine == null || machine.isBlank()) {
                reader.warn("machine", "no machine: — this recipe can never be crafted in any GUI");
            } else {
                reader.ref("machine", Kinds.MACHINE, machine);
            }
            if (!"SHAPED".equalsIgnoreCase(rawType) && !"SHAPELESS".equalsIgnoreCase(rawType)) {
                // EXACT_SLOT's old named-slot `inputs:` map (e.g. forge's input1/input2) is gone —
                // every positional machine, however many slots it has, is a SHAPED recipe now:
                // `ingredients:`/`pattern:` with a pattern exactly as wide as the machine's slot
                // count (a 2-slot machine like the forge just uses a 2-character pattern row). See
                // CLAUDE.md §9.2/recipe.md for the unified syntax. RecipeType.EXACT_SLOT still exists
                // as an internal marker for dynamic recipes (AnvilMachineHandler, alchemy, ...), just
                // not as something a YAML recipe can request.
                return LoadResult.failure("[" + filePath + "] Recipe " + id + ": type '" + rawType
                        + "' is not a valid YAML recipe type — use SHAPED (ingredients:/pattern:) or"
                        + " SHAPELESS (ingredients: as a list), even for a machine with named/fixed slots.");
            }
            RecipeType type = RecipeType.valueOf(rawType.toUpperCase());

            Map<String, RecipeIngredient> inputMap = new HashMap<>();
            List<RecipeIngredient> inputList = new ArrayList<>();
            int gridWidth = 3;
            Map<Character, RecipeIngredient> shapedLetters = new HashMap<>();

            if (type == RecipeType.SHAPELESS) {
                // Plain list, order/position irrelevant — mirrors vanilla's own shapeless-recipe
                // JSON convention (a flat `ingredients` list, vs. shaped's `key`+`pattern`).
                if (section.contains("pattern")) {
                    reader.warn("pattern", "SHAPELESS recipes match ingredients in any slot/order, so pattern: has no effect and is ignored");
                }
                List<? extends Map<?, ?>> ingredients = section.getMapList("ingredients");
                if (ingredients.isEmpty() && section.contains("inputs")) {
                    return LoadResult.failure("[" + filePath + "] Recipe " + id
                            + ": SHAPELESS recipes use ingredients: (a list) now, not inputs: — rename the key.");
                }
                for (int i = 0; i < ingredients.size(); i++) {
                    Map<?, ?> input = ingredients.get(i);
                    // `amount` is optional (defaults to 1) — previously a missing value threw an
                    // uncaught NPE from unboxing a null Integer, failing the whole file's load.
                    Object amountObj = input.get("amount");
                    int amount = amountObj instanceof Number n ? n.intValue() : 1;
                    Object item = input.containsKey("item") ? input.get("item") : input.get("material");
                    if (item == null) {
                        return LoadResult.failure("[" + filePath + "] Recipe " + id + ": ingredients[" + i + "] needs an item:");
                    }
                    reader.ref("ingredients." + i + ".item", Kinds.ITEM_OR_MATERIAL, String.valueOf(item));
                    inputList.add(new RecipeIngredient(String.valueOf(item), amount));
                }
            } else {
                // Letter-keyed SHAPED syntax (recipe-yaml rework note), the one input format every
                // positional machine uses now regardless of slot count/shape:
                //   ingredients: { a: {material, amount}, b: {...} }
                //   pattern: ["a a", " b "]                (a 2-slot machine: pattern: ["ab"])
                // Expanded into the existing numeric "row*width+col" inputMap representation — pure
                // syntax sugar, RecipeEngine never sees a letter. gridWidth is the widest pattern row.
                if (!section.contains("pattern")) {
                    return LoadResult.failure("[" + filePath + "] Recipe " + id
                            + ": SHAPED recipes need ingredients:/pattern: (e.g. pattern: [\"ab\"] for a"
                            + " 2-slot machine).");
                }
                ConfigurationSection ingredientsSec = section.getConfigurationSection("ingredients");
                Map<Character, RecipeIngredient> letters = shapedLetters;
                if (ingredientsSec != null) {
                    for (String key : ingredientsSec.getKeys(false)) {
                        if (key.length() != 1) {
                            reader.warn("ingredients." + key, "ingredient keys must be a single letter used in pattern: — '" + key + "' is ignored");
                            continue;
                        }
                        ConfigurationSection ingSec = ingredientsSec.getConfigurationSection(key);
                        if (ingSec == null) continue;
                        // `item:` accepted as an alias — SHAPELESS ingredients use `item:`, so writing
                        // it here too used to produce a null ingredient that silently never matched.
                        String material = ingSec.getString("material", ingSec.getString("item"));
                        if (material == null || material.isBlank()) {
                            return LoadResult.failure("[" + filePath + "] Recipe " + id
                                    + ": ingredient '" + key + "' needs a material: (a material or Valmora item id)");
                        }
                        reader.ref("ingredients." + key, Kinds.ITEM_OR_MATERIAL, material);
                        letters.put(key.charAt(0), new RecipeIngredient(material, ingSec.getInt("amount", 1)));
                    }
                }
                List<String> pattern = section.getStringList("pattern");
                java.util.Set<Character> usedLetters = new java.util.HashSet<>();
                for (String line : pattern) for (char c : line.toCharArray()) usedLetters.add(c);
                for (Character letter : letters.keySet()) {
                    if (!usedLetters.contains(letter)) {
                        reader.warn("ingredients." + letter, "ingredient '" + letter + "' is never used in pattern:");
                    }
                }
                gridWidth = pattern.stream().mapToInt(String::length).max().orElse(3);
                for (int row = 0; row < pattern.size(); row++) {
                    String line = pattern.get(row);
                    for (int col = 0; col < line.length(); col++) {
                        char c = line.charAt(col);
                        if (c == ' ') continue;
                        RecipeIngredient ing = letters.get(c);
                        if (ing == null) {
                            return LoadResult.failure("[" + filePath + "] Recipe " + id
                                    + ": pattern letter '" + c + "' has no matching ingredients entry");
                        }
                        inputMap.put(String.valueOf(row * gridWidth + col), ing);
                    }
                }
            }

            // outputs: is a plain list — most recipes have exactly one entry and need nothing more
            // than {item, amount}. A recipe with MORE than one entry must give every entry a slot:
            // naming the target GUI's OUTPUT component id (e.g. a 2-output "processor" machine's
            // `primary`/`byproduct`) — see RecipeOutput's javadoc for why this is required rather
            // than falling back to layout-scan order.
            List<RecipeOutput> outputs = new ArrayList<>();
            List<? extends Map<?, ?>> outputMaps = section.getMapList("outputs");
            if (outputMaps.isEmpty()) {
                return LoadResult.failure("[" + filePath + "] Recipe " + id + ": has no outputs: — nothing to craft.");
            }
            for (int i = 0; i < outputMaps.size(); i++) {
                Map<?, ?> outMap = outputMaps.get(i);
                Object amountObj = outMap.get("amount");
                int amount = amountObj instanceof Number n ? n.intValue() : 1;
                Object itemObj = outMap.get("item");
                if (itemObj == null) {
                    return LoadResult.failure("[" + filePath + "] Recipe " + id + ": outputs[" + i + "] needs an item:");
                }
                reader.ref("outputs." + i + ".item", Kinds.ITEM_OR_MATERIAL, String.valueOf(itemObj));
                Object slotObj = outMap.get("slot");
                outputs.add(new RecipeOutput(new RecipeIngredient(itemObj != null ? String.valueOf(itemObj) : null, amount),
                        slotObj != null ? String.valueOf(slotObj) : null));
            }
            if (outputs.size() > 1 && outputs.stream().anyMatch(o -> o.slot() == null)) {
                return LoadResult.failure("[" + filePath + "] Recipe " + id
                        + ": has " + outputs.size() + " outputs: entries — every entry needs a slot:"
                        + " naming which OUTPUT component id it goes to (see machines/*.yml's GUI and"
                        + " CLAUDE.md's recipe multi-output routing convention).");
            }

            // Parser-time sanity validation (recipe-yaml rework note) — catches recipes that could
            // never be crafted rather than letting them fail silently at match time.
            if (type == RecipeType.SHAPELESS) {
                int totalAmount = inputList.stream().mapToInt(RecipeIngredient::amount).sum();
                if (totalAmount > 64) {
                    return LoadResult.failure("[" + filePath + "] Recipe " + id
                            + ": SHAPELESS inputs require " + totalAmount + " total items, more than a stack (64).");
                }
            }
            for (RecipeOutput out : outputs) {
                if (out.ingredient().amount() > 64) {
                    return LoadResult.failure("[" + filePath + "] Recipe " + id
                            + ": output amount " + out.ingredient().amount() + " exceeds the maximum stack size (64).");
                }
            }

            CompiledEvent onCraft = null;
            if (section.contains("on-craft")) {
                onCraft = ScriptCompile.at("on-craft", () ->
                        plugin.getScriptModule().getEventParser().parseList(section.getStringList("on-craft")));
            }

            boolean keepDataOnUpgrade = section.getBoolean("keep-data-on-upgrade", true);
            String upgradeFrom = section.getString("upgrade-from", null);
            // A SHAPED recipe names its upgraded ingredient by pattern letter, a SHAPELESS one by
            // item id — neither is a key of the GUI's input map, so resolve both to "the first
            // input holding this item" (see RecipeEngine#upgradeSource).
            if (upgradeFrom != null && type == RecipeType.SHAPED && upgradeFrom.length() == 1) {
                RecipeIngredient source = shapedLetters.get(upgradeFrom.charAt(0));
                if (source == null) {
                    return LoadResult.failure("[" + filePath + "] Recipe " + id
                            + ": upgrade-from '" + upgradeFrom + "' is not a letter in ingredients:");
                }
                upgradeFrom = "item:" + source.item();
            } else if (upgradeFrom != null && type == RecipeType.SHAPELESS) {
                upgradeFrom = "item:" + upgradeFrom;
            }

            RecipeDefinition def = new RecipeDefinition(id, machine, type, inputMap, inputList, outputs, onCraft,
                    gridWidth, keepDataOnUpgrade, upgradeFrom);
            return LoadResult.success(def);
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Error parsing Recipe " + id + ": " + e.getMessage());
        }
    }

    /** Every key a (non-smithing) YAML recipe may use — anything else is reported as a likely typo. */
    private static final List<String> KNOWN_KEYS = List.of(
            "machine", "type", "ingredients", "pattern", "outputs", "on-craft", "keep-data-on-upgrade",
            "upgrade-from", "inputs", "cost");

    /**
     * Registers a real vanilla {@link org.bukkit.inventory.SmithingTransformRecipe} from a
     * {@code type: SMITHING} definition. Slot materials are vanilla-only (a {@code RecipeChoice}
     * matching a custom Valmora item's exact PDC state isn't practical here) — {@code template:},
     * {@code base:}, and {@code addition:} each take a material name or a list of them.
     * Returns a machine-less (never registered into any machine's recipe list) success so the
     * generic {@link org.nakii.valmora.infrastructure.config.YamlLoader}/{@code RecipeModule}
     * plumbing has something non-null to report success with.
     */
    private LoadResult<RecipeDefinition, String> parseSmithing(String id, ConfigurationSection section, String filePath) {
        org.bukkit.inventory.RecipeChoice template = parseChoice(section, "template");
        org.bukkit.inventory.RecipeChoice base = parseChoice(section, "base");
        org.bukkit.inventory.RecipeChoice addition = parseChoice(section, "addition");
        if (template == null || base == null || addition == null) {
            return LoadResult.failure("[" + filePath + "] SMITHING recipe '" + id
                    + "' needs template:, base:, and addition: (each a material name or list).");
        }

        ConfigurationSection resultSec = section.getConfigurationSection("result");
        if (resultSec == null) {
            return LoadResult.failure("[" + filePath + "] SMITHING recipe '" + id + "' is missing a result: block.");
        }
        String resultItemId = resultSec.getString("item");
        int resultAmount = resultSec.getInt("amount", 1);
        org.bukkit.inventory.ItemStack result;
        org.bukkit.Material resultMat = org.bukkit.Material.matchMaterial(String.valueOf(resultItemId));
        if (resultMat != null) {
            result = new org.bukkit.inventory.ItemStack(resultMat, resultAmount);
        } else {
            result = plugin.getItemManager().createItemStack(resultItemId);
            if (result == null) return LoadResult.failure("[" + filePath + "] SMITHING recipe '" + id + "': unknown result item '" + resultItemId + "'.");
            result.setAmount(resultAmount);
        }

        if (!org.nakii.valmora.infrastructure.config.YamlLoader.isValidating()) { // no side effects in a dry run
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "smithing_" + id);
            plugin.getServer().removeRecipe(key); // idempotent re-registration across /valmora reload
            org.bukkit.inventory.SmithingTransformRecipe recipe =
                    new org.bukkit.inventory.SmithingTransformRecipe(key, result, template, base, addition);
            plugin.getServer().addRecipe(recipe);
            REGISTERED_SMITHING_KEYS.add(key);
        }

        // Machine-less marker, skipped rather than registered under a null machine key
        // — see RecipeModule.loadRecipes().
        RecipeDefinition marker = new RecipeDefinition(id, null, RecipeType.EXACT_SLOT,
                Map.of(), List.of(), List.of(), null);
        return LoadResult.success(marker);
    }

    private org.bukkit.inventory.RecipeChoice parseChoice(ConfigurationSection section, String key) {
        List<org.bukkit.Material> materials = new ArrayList<>();
        ConfigReader reader = ConfigReader.of(section);
        for (String s : reader.stringList(key)) {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(s);
            if (mat != null) materials.add(mat);
            else reader.warn(key, "unknown material '" + s + "' — ignored", ConfigReader.materialHint(s));
        }
        return materials.isEmpty() ? null : new org.bukkit.inventory.RecipeChoice.MaterialChoice(materials);
    }
}
