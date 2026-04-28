package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
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
    private final Map<String, DynamicMachineHandler> dynamicHandlers = new HashMap<>();

    public RecipeEngine(Valmora plugin) {
        this.plugin = plugin;
    }

    public void registerHandler(String machineId, DynamicMachineHandler handler) {
        dynamicHandlers.put(machineId.toLowerCase(), handler);
    }

    public Optional<RecipeDefinition> match(String machineId, Map<String, ItemStack> inputs) {
        // 1. Check Dynamic Handlers
        DynamicMachineHandler dynamic = dynamicHandlers.get(machineId.toLowerCase());
        if (dynamic != null) {
            Optional<RecipeDefinition> dynamicMatch = dynamic.match(inputs);
            if (dynamicMatch.isPresent()) return dynamicMatch;
        }

        // 2. Check Static Yaml Recipes
        List<RecipeDefinition> recipes = plugin.getRecipeModule().getRecipesForMachine(machineId);

        for (RecipeDefinition recipe : recipes) {
            if (matches(recipe, inputs)) {
                return Optional.of(recipe);
            }
        }

        // 3. Check Vanilla Recipes (if machine is default or crafting)
        Optional<RecipeDefinition> vanillaMatch = matchVanillaRecipe(inputs);
        if (vanillaMatch.isPresent()) {
            return vanillaMatch;
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

        int providedCount = 0;
        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                try {
                    Integer.parseInt(entry.getKey());
                    providedCount++; // Count physical slots occupied, ignoring string IDs
                } catch (NumberFormatException ignored) {}
            }
        }

        if (providedCount != required.size()) return false;

        for (Map.Entry<String, RecipeIngredient> entry : required.entrySet()) {
            ItemStack stack = inputs.get(entry.getKey());
            if (stack == null) return false;
            if (!isSameItem(stack, entry.getValue().item()) || stack.getAmount() < entry.getValue().amount()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchShapeless(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        List<RecipeIngredient> required = recipe.getInputList();

        List<ItemStack> inputItems = new ArrayList<>();
        for (ItemStack stack : inputs.values()) {
            if (stack != null && stack.getType() != Material.AIR) {
                inputItems.add(stack.clone());
            }
        }

        if (inputItems.size() != required.size()) return false;

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

    public boolean consume(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        if (recipe.isVanilla()) {
            consumeVanilla(inputs);
            return true;
        }
        if (recipe.getType() == RecipeType.EXACT_SLOT) {
            for (Map.Entry<String, RecipeIngredient> entry : recipe.getInputMap().entrySet()) {
                ItemStack stack = inputs.get(entry.getKey());
                if (stack != null && stack.getType() != Material.AIR) {
                    stack.setAmount(stack.getAmount() - entry.getValue().amount());
                }
            }
            return true;
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            List<RecipeIngredient> required = new ArrayList<>(recipe.getInputList());
            for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
                try {
                    Integer.parseInt(entry.getKey()); // Target numeric slots only
                    ItemStack stack = entry.getValue();
                    if (stack == null || stack.getType() == Material.AIR) continue;
                    
                    for (int i = 0; i < required.size(); i++) {
                        RecipeIngredient ing = required.get(i);
                        if (isSameItem(stack, ing.item()) && stack.getAmount() >= ing.amount()) {
                            stack.setAmount(stack.getAmount() - ing.amount());
                            required.remove(i);
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            return true;
        } else if (recipe.getType() == RecipeType.SHAPED) {
            // Find the active offset so we know exactly which physical slots to deduct from
            Map<Integer, ItemStack> gridInput = new HashMap<>();
            Map<Integer, RecipeIngredient> gridRecipe = new HashMap<>();

            for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                    try { gridInput.put(Integer.parseInt(entry.getKey()), entry.getValue()); } catch (NumberFormatException ignored) {}
                }
            }
            for (Map.Entry<String, RecipeIngredient> entry : recipe.getInputMap().entrySet()) {
                try { gridRecipe.put(Integer.parseInt(entry.getKey()), entry.getValue()); } catch (NumberFormatException ignored) {}
            }

            int minX = 3, maxX = -1, minY = 3, maxY = -1;
            for (int slot : gridInput.keySet()) {
                int x = slot % 3; int y = slot / 3;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }

            int inputWidth = maxX - minX + 1, inputHeight = maxY - minY + 1;

            int recipeMinX = 3, recipeMaxX = -1, recipeMinY = 3, recipeMaxY = -1;
            for (int slot : gridRecipe.keySet()) {
                int x = slot % 3; int y = slot / 3;
                recipeMinX = Math.min(recipeMinX, x); recipeMaxX = Math.max(recipeMaxX, x);
                recipeMinY = Math.min(recipeMinY, y); recipeMaxY = Math.max(recipeMaxY, y);
            }

            int recipeWidth = recipeMaxX - recipeMinX + 1, recipeHeight = recipeMaxY - recipeMinY + 1;

            // Match offset loop
            for (int offsetY = 0; offsetY <= inputHeight - recipeHeight; offsetY++) {
                for (int offsetX = 0; offsetX <= inputWidth - recipeWidth; offsetX++) {
                    if (matchesPatternAt(gridInput, gridRecipe, minX, minY, offsetX, offsetY, recipeMinX, recipeMinY)) {
                        
                        // Deduct exactly from the offset slots that matched
                        for (Map.Entry<Integer, RecipeIngredient> entry : gridRecipe.entrySet()) {
                            int recipeSlot = entry.getKey();
                            int recipeX = recipeSlot % 3 - recipeMinX;
                            int recipeY = recipeSlot / 3 - recipeMinY;
                            int inputSlot = (minX + offsetX + recipeX) + (minY + offsetY + recipeY) * 3;
                            
                            ItemStack inputStack = gridInput.get(inputSlot);
                            if (inputStack != null) {
                                inputStack.setAmount(inputStack.getAmount() - entry.getValue().amount());
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void consumeVanilla(Map<String, ItemStack> inputs) {
        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            try {
                Integer.parseInt(entry.getKey());
                ItemStack stack = entry.getValue();
                if (stack != null && stack.getType() != Material.AIR) {
                    stack.setAmount(stack.getAmount() - 1);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private boolean matchShaped(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        Map<String, RecipeIngredient> recipeMap = recipe.getInputMap();

        Map<Integer, ItemStack> gridInput = new HashMap<>();
        Map<Integer, RecipeIngredient> gridRecipe = new HashMap<>();

        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    if (slot >= 0 && slot < 9) {
                        gridInput.put(slot, entry.getValue());
                    }
                } catch (NumberFormatException e) {
                    continue; // Ignore non-numeric keys, do NOT abort to matchExact!
                }
            }
        }

        for (Map.Entry<String, RecipeIngredient> entry : recipeMap.entrySet()) {
            try {
                int slot = Integer.parseInt(entry.getKey());
                if (slot >= 0 && slot < 9) {
                    gridRecipe.put(slot, entry.getValue());
                }
            } catch (NumberFormatException e) {
                continue; // Ignore non-numeric keys
            }
        }

        if (gridInput.isEmpty() || gridRecipe.isEmpty()) return false;

        if (gridInput.size() != gridRecipe.size()) return false;

        int minX = 3, maxX = -1, minY = 3, maxY = -1;
        for (int slot : gridInput.keySet()) {
            int x = slot % 3;
            int y = slot / 3;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        int inputWidth = maxX - minX + 1;
        int inputHeight = maxY - minY + 1;

        int recipeMinX = 3, recipeMaxX = -1, recipeMinY = 3, recipeMaxY = -1;
        for (int slot : gridRecipe.keySet()) {
            int x = slot % 3;
            int y = slot / 3;
            recipeMinX = Math.min(recipeMinX, x);
            recipeMaxX = Math.max(recipeMaxX, x);
            recipeMinY = Math.min(recipeMinY, y);
            recipeMaxY = Math.max(recipeMaxY, y);
        }

        int recipeWidth = recipeMaxX - recipeMinX + 1;
        int recipeHeight = recipeMaxY - recipeMinY + 1;

        if (inputWidth < recipeWidth || inputHeight < recipeHeight) return false;

        for (int offsetY = 0; offsetY <= inputHeight - recipeHeight; offsetY++) {
            for (int offsetX = 0; offsetX <= inputWidth - recipeWidth; offsetX++) {
                if (matchesPatternAt(gridInput, gridRecipe, minX, minY, offsetX, offsetY, recipeMinX, recipeMinY)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matchesPatternAt(Map<Integer, ItemStack> gridInput, Map<Integer, RecipeIngredient> gridRecipe,
                                   int inputBaseX, int inputBaseY, int offsetX, int offsetY,
                                   int recipeBaseX, int recipeBaseY) {
        for (Map.Entry<Integer, RecipeIngredient> entry : gridRecipe.entrySet()) {
            int recipeSlot = entry.getKey();
            int recipeX = recipeSlot % 3 - recipeBaseX;
            int recipeY = recipeSlot / 3 - recipeBaseY;

            int inputSlot = (inputBaseX + offsetX + recipeX) + (inputBaseY + offsetY + recipeY) * 3;
            ItemStack inputStack = gridInput.get(inputSlot);

            if (inputStack == null) return false;

            RecipeIngredient ingredient = entry.getValue();
            if (!isSameItem(inputStack, ingredient.item()) || inputStack.getAmount() < ingredient.amount()) {
                return false;
            }
        }
        return true;
    }

    private Optional<RecipeDefinition> matchVanillaRecipe(Map<String, ItemStack> inputs) {
        ItemStack[] matrix = new ItemStack[9];

        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    if (slot >= 0 && slot < 9) {
                        matrix[slot] = entry.getValue().clone();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        for (ItemStack stack : matrix) {
            if (stack != null && stack.getType() != Material.AIR) {
                World world = plugin.getServer().getWorlds().get(0);
                Recipe recipe = org.bukkit.Bukkit.getCraftingRecipe(matrix, world);
                if (recipe != null) {
                    ItemStack result = recipe.getResult();
                    if (result != null && result.getType() != Material.AIR) {
                        return Optional.of(RecipeDefinition.vanilla(result));
                    }
                }
                break;
            }
        }

        return Optional.empty();
    }

    private boolean isSameItem(ItemStack stack, String targetId) {
        if (stack == null || targetId == null) return false;

        if (stack.hasItemMeta()) {
            String valmoraId = stack.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (valmoraId != null && valmoraId.equalsIgnoreCase(targetId)) return true;
        }

        Material mat = Material.matchMaterial(targetId);
        return mat != null && stack.getType() == mat;
    }
}
