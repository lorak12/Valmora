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
            String machine = section.getString("machine");
            RecipeType type = RecipeType.valueOf(section.getString("type", "EXACT_SLOT").toUpperCase());

            Map<String, RecipeIngredient> inputMap = new HashMap<>();
            List<RecipeIngredient> inputList = new ArrayList<>();

            if (type == RecipeType.SHAPELESS) {
                List<? extends Map<?, ?>> inputs = section.getMapList("inputs");
                for (Map<?, ?> input : inputs) {
                    inputList.add(new RecipeIngredient((String) input.get("item"), (int) input.get("amount")));
                }
            } else {
                ConfigurationSection inputs = section.getConfigurationSection("inputs");
                if (inputs != null) {
                    for (String key : inputs.getKeys(false)) {
                        ConfigurationSection inputSec = inputs.getConfigurationSection(key);
                        if (inputSec != null) {
                            inputMap.put(key, new RecipeIngredient(inputSec.getString("item"), inputSec.getInt("amount")));
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
}
