package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class RecipeDefinition {
    private final String id;
    private final String machine;
    private final RecipeType type;
    private final Map<String, RecipeIngredient> inputMap;
    private final List<RecipeIngredient> inputList;
    private final Map<String, RecipeIngredient> outputs;
    private final CompiledEvent onCraft;
    private final boolean isVanilla;
    private final ItemStack vanillaResult;

    public RecipeDefinition(String id, String machine, RecipeType type,
                            Map<String, RecipeIngredient> inputMap,
                            List<RecipeIngredient> inputList,
                            Map<String, RecipeIngredient> outputs,
                            CompiledEvent onCraft) {
        this(id, machine, type, inputMap, inputList, outputs, onCraft, false, null);
    }

    private RecipeDefinition(String id, String machine, RecipeType type,
                            Map<String, RecipeIngredient> inputMap,
                            List<RecipeIngredient> inputList,
                            Map<String, RecipeIngredient> outputs,
                            CompiledEvent onCraft, boolean isVanilla, ItemStack vanillaResult) {
        this.id = id;
        this.machine = machine;
        this.type = type;
        this.inputMap = inputMap;
        this.inputList = inputList;
        this.outputs = outputs;
        this.onCraft = onCraft;
        this.isVanilla = isVanilla;
        this.vanillaResult = vanillaResult;
    }

    public static RecipeDefinition vanilla(ItemStack result) {
        return vanilla(result, null);
    }

    public static RecipeDefinition vanilla(ItemStack result, CompiledEvent onCraft) {
        Map<String, RecipeIngredient> outputs = new HashMap<>();
        outputs.put("result", new RecipeIngredient(result.getType().name(), result.getAmount()));
        return new RecipeDefinition("vanilla:" + result.getType().name(), "crafting_table",
            RecipeType.SHAPELESS, null, null, outputs, onCraft, true, result);
    }

    public String getId() { return id; }
    public String getMachine() { return machine; }
    public RecipeType getType() { return type; }
    public Map<String, RecipeIngredient> getInputMap() { return inputMap; }
    public List<RecipeIngredient> getInputList() { return inputList; }
    public Map<String, RecipeIngredient> getOutputs() { return outputs; }
    public CompiledEvent getOnCraft() { return onCraft; }
    public boolean isVanilla() { return isVanilla; }
    public ItemStack getVanillaResult() { return vanillaResult; }
}
