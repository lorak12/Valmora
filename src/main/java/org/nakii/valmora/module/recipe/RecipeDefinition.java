package org.nakii.valmora.module.recipe;

import org.nakii.valmora.api.scripting.CompiledEvent;
import java.util.Map;
import java.util.List;

public class RecipeDefinition {
    private final String id;
    private final String machine;
    private final RecipeType type;
    private final Map<String, RecipeIngredient> inputMap; // For EXACT_SLOT and SHAPED
    private final List<RecipeIngredient> inputList; // For SHAPELESS
    private final Map<String, RecipeIngredient> outputs;
    private final CompiledEvent onCraft;

    public RecipeDefinition(String id, String machine, RecipeType type, 
                            Map<String, RecipeIngredient> inputMap, 
                            List<RecipeIngredient> inputList, 
                            Map<String, RecipeIngredient> outputs, 
                            CompiledEvent onCraft) {
        this.id = id;
        this.machine = machine;
        this.type = type;
        this.inputMap = inputMap;
        this.inputList = inputList;
        this.outputs = outputs;
        this.onCraft = onCraft;
    }

    public String getId() { return id; }
    public String getMachine() { return machine; }
    public RecipeType getType() { return type; }
    public Map<String, RecipeIngredient> getInputMap() { return inputMap; }
    public List<RecipeIngredient> getInputList() { return inputList; }
    public Map<String, RecipeIngredient> getOutputs() { return outputs; }
    public CompiledEvent getOnCraft() { return onCraft; }
}
