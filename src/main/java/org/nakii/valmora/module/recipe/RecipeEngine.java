package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecipeEngine {

    private final Valmora plugin;

    public RecipeEngine(Valmora plugin) {
        this.plugin = plugin;
    }

    public Optional<RecipeDefinition> match(String machineId, Map<String, ItemStack> inputs) {
        List<RecipeDefinition> recipes = plugin.getRecipeModule().getRecipesForMachine(machineId);
        
        for (RecipeDefinition recipe : recipes) {
            if (matches(recipe, inputs)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    private boolean matches(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        return switch (recipe.getType()) {
            case EXACT_SLOT -> matchExact(recipe, inputs);
            case SHAPELESS -> matchShapeless(recipe, inputs);
            case SHAPED -> matchShaped(recipe, inputs);
        };
    }

    private boolean matchExact(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        Map<String, RecipeIngredient> required = recipe.getInputMap();
        
        // Use a set of input keys that actually have items
        Map<String, ItemStack> nonEmptyInputs = new HashMap<>();
        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                nonEmptyInputs.put(entry.getKey(), entry.getValue());
            }
        }

        if (nonEmptyInputs.size() != required.size()) return false;

        for (Map.Entry<String, RecipeIngredient> entry : required.entrySet()) {
            ItemStack stack = nonEmptyInputs.get(entry.getKey());
            if (stack == null) return false;
            if (!isSameItem(stack, entry.getValue().item()) || stack.getAmount() < entry.getValue().amount()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchShapeless(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        List<RecipeIngredient> required = recipe.getInputList();
        
        // Collate all non-empty input items into a list
        List<ItemStack> inputItems = new ArrayList<>();
        for (ItemStack stack : inputs.values()) {
            if (stack != null && stack.getType() != Material.AIR) {
                inputItems.add(stack.clone());
            }
        }

        if (inputItems.size() != required.size()) return false;

        // Use a boolean array to track which inputs have been "consumed" during matching
        boolean[] matched = new boolean[inputItems.size()];
        for (RecipeIngredient ingredient : required) {
            boolean found = false;
            for (int i = 0; i < inputItems.size(); i++) {
                if (!matched[i] && isSameItem(inputItems.get(i), ingredient.item()) && inputItems.get(i).getAmount() >= ingredient.amount()) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean matchShaped(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        // For custom GUIs, SHAPED is usually tied to the specific keys in the input map
        // If the keys are e.g. "a", "b", "c" representing grid positions, we treat it like EXACT_SLOT
        // But true SHAPED (like Minecraft) would allow the pattern to drift.
        // Assuming for now it's EXACT_SLOT but with the 'SHAPED' label in YAML.
        return matchExact(recipe, inputs);
    }

    private boolean isSameItem(ItemStack stack, String targetId) {
        if (stack == null || targetId == null) return false;
        
        // Check Valmora ID first
        if (stack.hasItemMeta()) {
            String valmoraId = stack.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (valmoraId != null && valmoraId.equalsIgnoreCase(targetId)) return true;
        }
        
        // Fallback to Material
        Material mat = Material.matchMaterial(targetId);
        return mat != null && stack.getType() == mat;
    }
}
