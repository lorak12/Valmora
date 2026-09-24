package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.DebugManager;
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

    /**
     * Unregisters a dynamic handler — call this from a dependent module's {@code onDisable()}
     * (e.g. Modifier, Alchemy) so a single-module reload doesn't leave a handler referencing a
     * disabled module's stale state registered on this engine.
     */
    public void unregisterHandler(String machineId) {
        dynamicHandlers.remove(machineId.toLowerCase());
    }

    /** Machine ids with a registered dynamic handler (anvil, alchemy, ...). */
    public java.util.Set<String> getHandlerIds() {
        return java.util.Set.copyOf(dynamicHandlers.keySet());
    }

    /**
     * Unified craft operation: matches, determines output, consumes ingredients.
     * Returns empty if no recipe matched or output could not be built.
     * All three steps happen atomically on the calling thread.
     */
    public Optional<CraftResult> craft(String machineId, Map<String, ItemStack> inputs) {
        return craft(machineId, inputs, null);
    }

    public Optional<CraftResult> craft(String machineId, Map<String, ItemStack> inputs, @Nullable Player player) {
        Optional<RecipeDefinition> matched = match(machineId, inputs, player);
        if (matched.isEmpty()) {
            DebugManager.log("recipe", "craft(machine=" + machineId + ", player="
                    + (player != null ? player.getName() : "none") + ") — NO MATCH, inputs=" + describeInputs(inputs));
            return Optional.empty();
        }

        RecipeDefinition recipe = matched.get();
        List<CraftOutput> outputs = buildOutputs(recipe);
        if (outputs.isEmpty()) {
            DebugManager.log("recipe", "craft(machine=" + machineId + ") matched recipe='" + recipe.getId()
                    + "' but produced NO OUTPUTS — check outputs: block");
            return Optional.empty();
        }
        DebugManager.log("recipe", "craft(machine=" + machineId + ", player="
                + (player != null ? player.getName() : "none") + ") matched recipe='" + recipe.getId()
                + "' outputs=" + outputs.size());

        // "keep-data-on-upgrade" (recipe-yaml rework note) — carry enchants/modifiers/durability/
        // name from the designated source ingredient onto the primary (first) output before it's
        // returned. Only meaningful for a single-output recipe (the only kind that uses this flag).
        if (recipe.isKeepDataOnUpgrade() && recipe.getUpgradeFrom() != null) {
            ItemStack source = upgradeSource(recipe.getUpgradeFrom(), inputs);
            CraftOutput first = outputs.get(0);
            if (source != null && source.getType() != Material.AIR) {
                outputs = new ArrayList<>(outputs);
                outputs.set(0, new CraftOutput(ItemDataCarrier.carryForward(plugin, source, first.item()), first.slot()));
            }
        }

        consume(recipe, inputs);
        return Optional.of(new CraftResult(outputs, recipe, recipe.getOnCraft()));
    }

    /**
     * The input stack whose data carries onto the output. {@code upgrade-from} is either a GUI
     * input slot id, or — resolved at parse time from a SHAPED pattern letter or a SHAPELESS item
     * id — {@code "item:<id>"}, meaning the first input holding that item.
     */
    private ItemStack upgradeSource(String upgradeFrom, Map<String, ItemStack> inputs) {
        if (!upgradeFrom.startsWith("item:")) return inputs.get(upgradeFrom);
        String itemId = upgradeFrom.substring(5);
        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            try {
                Integer.parseInt(entry.getKey()); // positional keys only — each stack appears once
            } catch (NumberFormatException e) {
                continue;
            }
            if (isSameItem(entry.getValue(), itemId)) return entry.getValue();
        }
        return null;
    }

    private static String describeInputs(Map<String, ItemStack> inputs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null || item.getType() == Material.AIR) continue;
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(item.getType()).append("x").append(item.getAmount());
            first = false;
        }
        return sb.append("}").toString();
    }

    /** Builds every {@code outputs:} entry, not just the first — see {@link CraftResult}. */
    private List<CraftOutput> buildOutputs(RecipeDefinition recipe) {
        if (recipe.isVanilla()) {
            ItemStack output = recipe.getVanillaResult().clone();
            return output.getType() == org.bukkit.Material.AIR ? List.of() : List.of(new CraftOutput(output, null));
        }
        if (recipe.getOutputs() == null || recipe.getOutputs().isEmpty()) return List.of();

        List<CraftOutput> built = new ArrayList<>();
        for (RecipeOutput recipeOutput : recipe.getOutputs()) {
            RecipeIngredient outputIngredient = recipeOutput.ingredient();
            ItemStack output;
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(outputIngredient.item());
            if (mat == null) {
                output = plugin.getItemManager().createItemStack(outputIngredient.item());
                if (output != null) output.setAmount(outputIngredient.amount());
            } else {
                output = new ItemStack(mat, outputIngredient.amount());
            }
            if (output == null || output.getType() == org.bukkit.Material.AIR) continue;
            // Ensure every item coming out of a machine is a Valmora-formatted item
            built.add(new CraftOutput(plugin.getItemManager().getItemTranslator().translate(output), recipeOutput.slot()));
        }
        return built;
    }

    public Optional<RecipeDefinition> match(String machineId, Map<String, ItemStack> inputs) {
        return match(machineId, inputs, null);
    }

    public Optional<RecipeDefinition> match(String machineId, Map<String, ItemStack> inputs, @Nullable Player player) {
        // 1. Check Dynamic Handlers
        DynamicMachineHandler dynamic = dynamicHandlers.get(machineId.toLowerCase());
        if (dynamic != null) {
            Optional<RecipeDefinition> dynamicMatch = dynamic.match(inputs, player);
            if (dynamicMatch.isPresent()) return dynamicMatch;
        }

        // 2. Check Static Yaml Recipes
        List<RecipeDefinition> recipes = plugin.getRecipeModule().getRecipesForMachine(machineId);

        for (RecipeDefinition recipe : recipes) {
            if (matches(recipe, inputs)) {
                return Optional.of(recipe);
            }
        }

        // 3. Check Vanilla Recipes — scoped to the crafting-table passthrough machine only.
        // Previously machine-agnostic, so e.g. an anvil/forge/alchemy GUI would silently also
        // match a vanilla crafting recipe if the same items happened to sit in numbered slots.
        if (vanillaFallbackMachines().contains(machineId.toLowerCase())) {
            Optional<RecipeDefinition> vanillaMatch = matchVanillaRecipe(inputs);
            if (vanillaMatch.isPresent()) {
                return vanillaMatch;
            }
        }

        return Optional.empty();
    }

    /** HC-104: {@code recipes.vanilla-fallback-machines} — machine ids that fall through to
     *  vanilla crafting-table recipes when nothing else matches. */
    private java.util.Set<String> vanillaFallbackMachines() {
        if (plugin == null || plugin.getConfig() == null) return java.util.Set.of("crafting_table");
        java.util.List<String> configured = plugin.getConfig().getStringList("recipes.vanilla-fallback-machines");
        if (configured.isEmpty()) return java.util.Set.of("crafting_table");
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String id : configured) set.add(id.toLowerCase());
        return set;
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

        // GuiSession.buildInputSnapshot() publishes every INPUT slot's item TWICE — once under its
        // component id ("input1", "base", ...) and once under a zero-indexed numeric alias ("0",
        // "1", ...), see CLAUDE.md's "Machine input key conventions". EXACT_SLOT recipes are keyed
        // by the named ids, so counting occupied physical slots must skip the numeric aliases —
        // otherwise every placed item is counted twice and this never matches (providedCount always
        // double `required.size()`).
        int providedCount = 0;
        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            if (isNumericKey(entry.getKey())) continue;
            ItemStack stack = entry.getValue();
            if (stack != null && stack.getType() != Material.AIR) {
                providedCount++;
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
        if (recipe.getConsumeHandler() != null) {
            recipe.getConsumeHandler().accept(inputs);
            return true;
        }
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
            int width = gridWidthFor(recipe);
            Map<Integer, ItemStack> gridInput = new HashMap<>();
            Map<Integer, RecipeIngredient> gridRecipe = recipeGrid(recipe, width);

            for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                    try { gridInput.put(Integer.parseInt(entry.getKey()), entry.getValue()); } catch (NumberFormatException ignored) {}
                }
            }

            int[] inputBox = boundingBox(gridInput.keySet(), width);
            int[] recipeBox = boundingBox(gridRecipe.keySet(), width);
            int inputWidth = inputBox[2], inputHeight = inputBox[3];
            int recipeWidth = recipeBox[2], recipeHeight = recipeBox[3];

            // Match offset loop
            for (int offsetY = 0; offsetY <= inputHeight - recipeHeight; offsetY++) {
                for (int offsetX = 0; offsetX <= inputWidth - recipeWidth; offsetX++) {
                    if (matchesPatternAt(gridInput, gridRecipe, width, inputBox[0], inputBox[1], offsetX, offsetY, recipeBox[0], recipeBox[1])) {

                        // Deduct exactly from the offset slots that matched
                        for (Map.Entry<Integer, RecipeIngredient> entry : gridRecipe.entrySet()) {
                            int recipeSlot = entry.getKey();
                            int recipeX = recipeSlot % width - recipeBox[0];
                            int recipeY = recipeSlot / width - recipeBox[1];
                            int inputSlot = (inputBox[0] + offsetX + recipeX) + (inputBox[1] + offsetY + recipeY) * width;

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

    /** {@code [minX, minY, width, height]} of a set of numeric slot keys on a grid of the given
     *  column width — generalizes the old hardcoded-3-wide bounding-box math (RecipeEngine used to
     *  assume every SHAPED recipe lived on a fixed 3x3 crafting grid; machines/recipes can now
     *  declare their own width via the letter-pattern syntax, see RecipeDefinitionParser). */
    private int[] boundingBox(java.util.Set<Integer> slots, int width) {
        int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
        for (int slot : slots) {
            int x = slot % width, y = slot / width;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        if (maxX < 0) return new int[]{0, 0, 0, 0};
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
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

    /**
     * The column count the machine's input slots are numbered on: its {@code machines/*.yml}
     * {@code shape:} when declared, otherwise the recipe's own pattern width (the pre-machine-layer
     * behavior). Fixed 2026-09-24 — matching used to always use the pattern width, so a pattern
     * narrower than the machine (a 1-wide column on the 3x3 crafting table) never matched, despite
     * the documented "a smaller pattern slides to fit" behavior.
     */
    private int gridWidthFor(RecipeDefinition recipe) {
        var machines = plugin != null ? plugin.getMachineModule() : null;
        if (machines != null && recipe.getMachine() != null) {
            var shape = machines.getRegistry().get(recipe.getMachine()).map(m -> m.getShape()).orElse(null);
            if (shape != null && shape.cols() > 0) return shape.cols();
        }
        return recipe.getGridWidth();
    }

    /** The recipe's pattern cells, re-numbered from its own pattern width onto a {@code width}-column grid. */
    private Map<Integer, RecipeIngredient> recipeGrid(RecipeDefinition recipe, int width) {
        int patternWidth = recipe.getGridWidth();
        Map<Integer, RecipeIngredient> grid = new HashMap<>();
        for (Map.Entry<String, RecipeIngredient> entry : recipe.getInputMap().entrySet()) {
            try {
                int slot = Integer.parseInt(entry.getKey());
                if (slot < 0) continue;
                grid.put((slot / patternWidth) * width + (slot % patternWidth), entry.getValue());
            } catch (NumberFormatException ignored) {
                // non-numeric keys aren't pattern cells
            }
        }
        return grid;
    }

    private boolean matchShaped(RecipeDefinition recipe, Map<String, ItemStack> inputs) {
        int width = gridWidthFor(recipe);
        if (width < recipe.getGridWidth()) return false; // pattern wider than the machine can never fit

        Map<Integer, ItemStack> gridInput = new HashMap<>();
        Map<Integer, RecipeIngredient> gridRecipe = recipeGrid(recipe, width);

        for (Map.Entry<String, ItemStack> entry : inputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    if (slot >= 0) {
                        gridInput.put(slot, entry.getValue());
                    }
                } catch (NumberFormatException e) {
                    continue; // Ignore non-numeric keys, do NOT abort to matchExact!
                }
            }
        }

        if (gridInput.isEmpty() || gridRecipe.isEmpty()) return false;

        if (gridInput.size() != gridRecipe.size()) return false;

        int[] inputBox = boundingBox(gridInput.keySet(), width);
        int[] recipeBox = boundingBox(gridRecipe.keySet(), width);
        int inputWidth = inputBox[2], inputHeight = inputBox[3];
        int recipeWidth = recipeBox[2], recipeHeight = recipeBox[3];

        if (inputWidth < recipeWidth || inputHeight < recipeHeight) return false;

        // Bounding-box translation: tries every valid offset, so a pattern smaller than the
        // machine's full grid naturally slides to every position it fits (the recipe-yaml note's
        // "2x2 fits all 4 corners" / "1-2 row pattern slides vertically" behavior falls out of this
        // for free — an explicit full-height pattern has exactly one vertical offset, which is
        // already the "exact position" case the note describes, with no extra flag needed).
        for (int offsetY = 0; offsetY <= inputHeight - recipeHeight; offsetY++) {
            for (int offsetX = 0; offsetX <= inputWidth - recipeWidth; offsetX++) {
                if (matchesPatternAt(gridInput, gridRecipe, width, inputBox[0], inputBox[1], offsetX, offsetY, recipeBox[0], recipeBox[1])) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matchesPatternAt(Map<Integer, ItemStack> gridInput, Map<Integer, RecipeIngredient> gridRecipe,
                                   int width, int inputBaseX, int inputBaseY, int offsetX, int offsetY,
                                   int recipeBaseX, int recipeBaseY) {
        for (Map.Entry<Integer, RecipeIngredient> entry : gridRecipe.entrySet()) {
            int recipeSlot = entry.getKey();
            int recipeX = recipeSlot % width - recipeBaseX;
            int recipeY = recipeSlot / width - recipeBaseY;

            int inputSlot = (inputBaseX + offsetX + recipeX) + (inputBaseY + offsetY + recipeY) * width;
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

    private boolean isNumericKey(String key) {
        try {
            Integer.parseInt(key);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
