package org.nakii.valmora.module.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeDefinitionParser {

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

            String machine = section.getString("machine");
            RecipeType type = RecipeType.valueOf(section.getString("type", "EXACT_SLOT").toUpperCase());

            Map<String, RecipeIngredient> inputMap = new HashMap<>();
            List<RecipeIngredient> inputList = new ArrayList<>();

            if (type == RecipeType.SHAPELESS) {
                List<? extends Map<?, ?>> inputs = section.getMapList("inputs");
                for (Map<?, ?> input : inputs) {
                    // `amount` is optional (defaults to 1) — previously a missing value threw an
                    // uncaught NPE from unboxing a null Integer, failing the whole file's load.
                    Object amountObj = input.get("amount");
                    int amount = amountObj instanceof Number n ? n.intValue() : 1;
                    inputList.add(new RecipeIngredient((String) input.get("item"), amount));
                }
            } else {
                ConfigurationSection inputs = section.getConfigurationSection("inputs");
                if (inputs != null) {
                    for (String key : inputs.getKeys(false)) {
                        ConfigurationSection inputSec = inputs.getConfigurationSection(key);
                        if (inputSec != null) {
                            inputMap.put(key, new RecipeIngredient(inputSec.getString("item"), inputSec.getInt("amount", 1)));
                        } else {
                            Object itemObj = inputs.get(key + ".item");
                            Object amountObj = inputs.get(key + ".amount");
                            if (itemObj != null) {
                                int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 1;
                                inputMap.put(key, new RecipeIngredient(String.valueOf(itemObj), amount));
                            }
                        }
                    }
                }
            }

            Map<String, RecipeIngredient> outputs = new HashMap<>();
            ConfigurationSection outputsSec = section.getConfigurationSection("outputs");
            if (outputsSec != null) {
                for (String key : outputsSec.getKeys(false)) {
                    ConfigurationSection outSec = outputsSec.getConfigurationSection(key);
                    if (outSec != null) {
                        outputs.put(key, new RecipeIngredient(outSec.getString("item"), outSec.getInt("amount")));
                    } else {
                        Object itemObj = outputsSec.get(key + ".item");
                        Object amountObj = outputsSec.get(key + ".amount");
                        if (itemObj != null) {
                            int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 1;
                            outputs.put(key, new RecipeIngredient(String.valueOf(itemObj), amount));
                        }
                    }
                }
            }

            CompiledEvent onCraft = null;
            if (section.contains("on-craft")) {
                onCraft = plugin.getScriptModule().getEventParser().parseList(section.getStringList("on-craft"));
            }

            RecipeDefinition def = new RecipeDefinition(id, machine, type, inputMap, inputList, outputs, onCraft);
            return LoadResult.success(def);
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Error parsing Recipe " + id + ": " + e.getMessage());
        }
    }

    /**
     * Registers a real vanilla {@link org.bukkit.inventory.SmithingTransformRecipe} from a
     * {@code type: SMITHING} definition. Slot materials are vanilla-only (a {@code RecipeChoice}
     * matching a custom Valmora item's exact PDC state isn't practical here) — {@code template:},
     * {@code base:}, and {@code addition:} each take a material name or a list of them.
     * Returns a machine-less (never registered into any machine's recipe list) success so the
     * generic {@link org.nakii.valmora.infrastructure.config.YamlLoader}/{@code RecipeModule}
     * plumbing can keep treating this the same way it already does {@code anvil_templates.yml}.
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

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "smithing_" + id);
        plugin.getServer().removeRecipe(key); // idempotent re-registration across /valmora reload
        org.bukkit.inventory.SmithingTransformRecipe recipe =
                new org.bukkit.inventory.SmithingTransformRecipe(key, result, template, base, addition);
        plugin.getServer().addRecipe(recipe);

        // Machine-less marker, mirroring how anvil_templates.yml's config-only entries are
        // parsed-but-not-registered — see RecipeModule.loadRecipes().
        RecipeDefinition marker = new RecipeDefinition(id, null, RecipeType.EXACT_SLOT,
                Map.of(), List.of(), Map.of(), null);
        return LoadResult.success(marker);
    }

    private org.bukkit.inventory.RecipeChoice parseChoice(ConfigurationSection section, String key) {
        List<org.bukkit.Material> materials = new ArrayList<>();
        if (section.isList(key)) {
            for (String s : section.getStringList(key)) {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(s);
                if (mat != null) materials.add(mat);
            }
        } else if (section.isString(key)) {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(section.getString(key, ""));
            if (mat != null) materials.add(mat);
        }
        return materials.isEmpty() ? null : new org.bukkit.inventory.RecipeChoice.MaterialChoice(materials);
    }
}
