package org.nakii.valmora.module.gui.renderer;

import com.google.gson.Gson;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.gui.*;
import org.nakii.valmora.module.gui.components.*;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.module.recipe.RecipeEngine;
import org.nakii.valmora.module.recipe.RecipeIngredient;
import org.nakii.valmora.module.recipe.RecipeOutput;
import org.nakii.valmora.util.Formatter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiRenderer {

    private final Valmora plugin;
    private final Map<PaginatedComponent, Integer> paginatedCounters = new HashMap<>();

    /**
     * Parsed-condition cache for PAGINATED component states (added 2026-08-07 — {@link
     * #findMatchingState} previously re-parsed every candidate state's raw condition string from
     * scratch on every single render, for every list item, every render tick). A {@code
     * GuiRenderer} is constructed fresh at essentially every call site (see the ~14 {@code new
     * GuiRenderer(plugin)} sites across the module), so an instance field would be discarded
     * immediately — this has to be static to actually cache anything across renders. Safe to
     * share across reloads: a {@link org.nakii.valmora.api.scripting.Condition} is a pure
     * function of its source string, not tied to a particular loaded GUI/registry generation.
     */
    private static final Map<String, org.nakii.valmora.api.scripting.Condition> CONDITION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Drops cached conditions (module disable), so conditions compiled against a previous script
     *  engine generation, or strings no longer used by any GUI, don't outlive a reload. */
    public static void clearConditionCache() {
        CONDITION_CACHE.clear();
    }

    public GuiRenderer(Valmora plugin) {
        this.plugin = plugin;
    }

    public void render(GuiSession session) {
        Inventory inv = session.getInventory();

        Map<Integer, ItemStack> savedInputItems = saveInputItems(session);
        Map<Integer, ItemStack> savedOutputItems = saveOutputItems(session);
        Map<Integer, ItemStack> savedStorageItems = saveStorageItems(session);

        // Cache input snapshot BEFORE clearing so mid-render variable
        // resolution (e.g. $gui.input.ingredient.available_enchants$)
        // still sees the items the player placed.
        session.snapshotInputs();

        inv.clear();
        this.paginatedCounters.clear();

        renderLayout(session, inv);

        restoreInputItems(session, savedInputItems);
        restoreInputItems(session, savedStorageItems);
        session.clearInputSnapshot();
        updateOutputSlot(session, savedOutputItems);
    }

    /**
     * Captures current STORAGE slot contents before the inventory is cleared. On a session's
     * very first render the inventory is empty, so any preloaded contents (from item PDC or
     * the async DB load) are seeded in here instead — see {@link GuiSession#pollInitialStorageContents}.
     */
    private Map<Integer, ItemStack> saveStorageItems(GuiSession session) {
        Map<Integer, ItemStack> saved = new HashMap<>();
        GuiDefinition def = session.getDefinition();
        Inventory inv = session.getInventory();

        for (StorageComponent storage : getStorageComponents(def)) {
            List<Integer> slots = findAllSlotsForComponent(def, storage);
            ItemStack[] initial = session.pollInitialStorageContents(storage.getStorageId());
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i);
                ItemStack current = inv.getItem(slot);
                if (current == null && initial != null && i < initial.length && initial[i] != null) {
                    current = initial[i];
                }
                saved.put(slot, current);
            }
        }
        return saved;
    }

    private List<StorageComponent> getStorageComponents(GuiDefinition def) {
        java.util.LinkedHashSet<StorageComponent> set = new java.util.LinkedHashSet<>();
        for (GuiComponent component : def.getComponents().values()) {
            if (component instanceof StorageComponent storage) {
                set.add(storage);
            }
        }
        return new ArrayList<>(set);
    }

    private Map<Integer, ItemStack> saveInputItems(GuiSession session) {
        Map<Integer, ItemStack> saved = new HashMap<>();
        GuiDefinition def = session.getDefinition();
        Inventory inv = session.getInventory();

        for (InputComponent input : getInputComponents(def)) {
            for (int slot : findAllSlotsForComponent(def, input)) {
                saved.put(slot, inv.getItem(slot));
            }
        }
        return saved;
    }

    private Map<Integer, ItemStack> saveOutputItems(GuiSession session) {
        Map<Integer, ItemStack> saved = new HashMap<>();
        GuiDefinition def = session.getDefinition();
        Inventory inv = session.getInventory();

        for (OutputComponent output : getOutputComponents(def)) {
            for (int slot : findAllSlotsForComponent(def, output)) {
                saved.put(slot, inv.getItem(slot));
            }
        }
        return saved;
    }

    private void restoreInputItems(GuiSession session, Map<Integer, ItemStack> savedItems) {
        Inventory inv = session.getInventory();
        for (Map.Entry<Integer, ItemStack> entry : savedItems.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue());
        }
    }

    private void updateOutputSlot(GuiSession session, Map<Integer, ItemStack> previousOutput) {
        GuiDefinition def = session.getDefinition();
        Inventory inv = session.getInventory();

        Map<String, Integer> outputSlotsById = def.findOutputSlotsById();
        if (outputSlotsById.isEmpty()) return;

        Optional<RecipeDefinition> match = matchRecipe(session);
        if (match.isEmpty()) {
            // No recipe matches the CURRENT inputs. For a live-preview GUI (crafting_table, anvil,
            // ...) that's already correct — GuiListener.updateRecipeOutput ran first and already
            // nulled/updated every output slot to match, so previousOutput.get(slot) is already
            // null/up to date here too. But for a "gui_force_craft on-slot-update" auto-craft GUI
            // (press, forge, ...) this render() runs AFTER the craft already consumed every input and
            // placed the real result here — inputs are now empty so matchRecipe() naturally finds
            // nothing, and unconditionally nulling every slot would discard the just-crafted item(s)
            // before the player could ever collect them. Falling back to whatever was already in each
            // slot preserves that result without needing to special-case which machines auto-craft.
            for (int slot : outputSlotsById.values()) inv.setItem(slot, previousOutput.get(slot));
            return;
        }

        RecipeDefinition recipe = match.get();

        // Vanilla/dynamic recipes carry ONE pre-built result item (preserves enchantments etc.) —
        // preview it into the sole/first OUTPUT slot.
        if (recipe.isVanilla() && recipe.getVanillaResult() != null) {
            ItemStack result = recipe.getVanillaResult().clone();
            plugin.getItemManager().getItemTranslator().translate(result);
            outputSlotsById.values().stream().findFirst().ifPresent(slot -> inv.setItem(slot, result));
            return;
        }

        if (recipe.getOutputs() == null || recipe.getOutputs().isEmpty()) return;

        for (RecipeOutput recipeOutput : recipe.getOutputs()) {
            RecipeIngredient ingredient = recipeOutput.ingredient();
            Integer slot = recipeOutput.slot() != null
                    ? outputSlotsById.get(recipeOutput.slot())
                    : outputSlotsById.values().stream().findFirst().orElse(null);
            if (slot == null) continue; // named slot doesn't exist in this GUI — nothing to preview into

            Material mat = Material.matchMaterial(ingredient.item());
            if (mat == null) {
                ItemStack custom = plugin.getItemManager().createItemStack(ingredient.item());
                if (custom != null) {
                    custom.setAmount(ingredient.amount());
                    inv.setItem(slot, custom);
                    continue;
                }
                mat = Material.BARRIER;
            }
            inv.setItem(slot, new ItemStack(mat, ingredient.amount()));
        }
    }

    private Optional<RecipeDefinition> matchRecipe(GuiSession session) {
        RecipeEngine engine = plugin.getRecipeModule().getRecipeEngine();
        if (engine == null) return Optional.empty();

        // Use the centralized snapshot method from GuiSession
        Map<String, ItemStack> inputSnapshot = session.getInputSnapshot();
        return engine.match(session.getDefinition().getMachine(), inputSnapshot);
    }

    private void renderLayout(GuiSession session, Inventory inv) {
        GuiDefinition def = session.getDefinition();
        List<List<Character>> layout = def.getLayout();

        for (int row = 0; row < layout.size(); row++) {
            List<Character> rowChars = layout.get(row);
            for (int col = 0; col < rowChars.size(); col++) {
                char c = rowChars.get(col);
                int slot = row * 9 + col;
                if (slot >= inv.getSize()) break;

                GuiComponent component = def.getComponents().get(c);
                if (component instanceof DisplayComponent display) {
                    GuiItemStack itemDef = display.getDisplayItem();
                    if (!display.getStates().isEmpty()) {
                        PaginatedState state = findMatchingState(display.getStates(), session, null, null);
                        if (state != null) itemDef = state.displayItem();
                    }
                    if (itemDef != null) {
                        inv.setItem(slot, createItemStack(itemDef, session, null, null));
                    }
                } else if (component instanceof PaginatedComponent paginated) {
                    renderPaginatedSlot(session, inv, slot, paginated, c);
                } else if (component instanceof PageButtonComponent button) {
                    renderPageButton(session, inv, slot, button);
                }
            }
        }
    }

    private List<Integer> findAllSlotsForComponent(GuiDefinition def, GuiComponent target) {
        List<Integer> slots = new ArrayList<>();
        List<List<Character>> layout = def.getLayout();
        char targetChar = findComponentChar(def, target);
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (row.get(c) == targetChar) {
                    slots.add(r * 9 + c);
                }
            }
        }
        return slots;
    }

    private char findComponentChar(GuiDefinition def, GuiComponent target) {
        for (Map.Entry<Character, GuiComponent> entry : def.getComponents().entrySet()) {
            if (entry.getValue() == target) {
                return entry.getKey();
            }
        }
        return 0;
    }

    private List<InputComponent> getInputComponents(GuiDefinition def) {
        List<InputComponent> inputs = new ArrayList<>();
        for (GuiComponent component : def.getComponents().values()) {
            if (component instanceof InputComponent input) {
                inputs.add(input);
            }
        }
        return inputs;
    }

    private List<OutputComponent> getOutputComponents(GuiDefinition def) {
        List<OutputComponent> outputs = new ArrayList<>();
        for (GuiComponent component : def.getComponents().values()) {
            if (component instanceof OutputComponent output) {
                outputs.add(output);
            }
        }
        return outputs;
    }

    private void renderPaginatedSlot(GuiSession session, Inventory inv, int slot, PaginatedComponent paginated, char currentChar) {
        List<?> items = resolveList(paginated.getListExpression(), session);
        if (items == null) return;
        items = sortList(items, paginated);

        int itemsPerPage;
        int indexInPage;

        if (paginated.getPath() != null && !paginated.getPath().isEmpty()) {
            itemsPerPage = paginated.getPath().length();
            indexInPage = paginated.getPath().indexOf(currentChar);
            
            if (indexInPage == -1) return; 
        } else {
            itemsPerPage = countSlotsForComponent(session.getDefinition(), paginated);
            indexInPage = paginatedCounters.getOrDefault(paginated, 0);
            
            paginatedCounters.put(paginated, indexInPage + 1);
        }

        int itemIndex = session.getCurrentPage() * itemsPerPage + indexInPage;

        if (itemIndex < items.size()) {
            Object loopItem = items.get(itemIndex);
            PaginatedState state = findMatchingState(paginated.getStates(), session, loopItem, paginated.getIteratorName());
            if (state != null) {
                inv.setItem(slot, createItemStack(state.displayItem(), session, loopItem, paginated.getIteratorName()));
            }
        }
    }

    private void renderPageButton(GuiSession session, Inventory inv, int slot, PageButtonComponent button) {
        boolean hasNext = hasNextPage(session);
        boolean hasPrev = session.getCurrentPage() > 0;

        boolean active = button.isNext() ? hasNext : hasPrev;
        GuiItemStack itemDef = active ? button.getDisplayItem() : button.getFallbackItem();

        if (itemDef != null) {
            inv.setItem(slot, createItemStack(itemDef, session, null, null));
        }
    }

    public int countSlotsForComponent(GuiDefinition def, GuiComponent target) {
        int count = 0;
        for (List<Character> row : def.getLayout()) {
            for (char c : row) {
                if (def.getComponents().get(c) == target) count++;
            }
        }
        return count;
    }

    public PaginatedState findMatchingState(List<PaginatedState> states, GuiSession session, Object loopItem, String iteratorName) {
        if (states == null || states.isEmpty()) return null;
        
        GuiExecutionContext context = new GuiExecutionContext(session.getPlayer(), session);
        if (loopItem != null && iteratorName != null) {
            context.setLoopVar(iteratorName, loopItem);
        }
        PaginatedState defaultState = null;

        for (PaginatedState state : states) {
            String condStr = state.condition();
            if (condStr.equalsIgnoreCase("default")) {
                defaultState = state;
                continue;
            }

            if (loopItem != null && condStr.equalsIgnoreCase(loopItem.toString())) return state;
            
            // Try evaluating as a script condition
            try {
                var condition = CONDITION_CACHE.computeIfAbsent(condStr, plugin.getScriptModule().getConditionParser()::parse);
                if (condition.evaluate(context)) {
                    return state;
                }
            } catch (Exception ignored) {}
        }
        
        return defaultState;
    }

    public ItemStack createItemStack(GuiItemStack def, GuiSession session, Object loopItem, String iteratorName) {
        if (def == null) return null;

        String matStr = resolveVariables(def.material(), session, loopItem, iteratorName);
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.BARRIER;

        ItemStack item = new ItemStack(mat, def.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (mat == Material.PLAYER_HEAD && meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(session.getPlayer());
            }

            String name = resolveVariables(def.name(), session, loopItem, iteratorName);
            meta.displayName(Formatter.format(name));

            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) {
                lore.add(resolveVariables(line, session, loopItem, iteratorName));
            }
            // HC-292: guards against exceeding the client's ~256-line lore cap — a runaway
            // dynamic/looped lore list (e.g. resolveVariables expanding a long collection) used to
            // just get sent as-is with no warning.
            int maxLoreLines = plugin != null ? plugin.getConfig().getInt("gui.max-lore-lines", 0) : 0;
            if (maxLoreLines > 0 && lore.size() > maxLoreLines) {
                plugin.getLogger().warning("[GUI] Item '" + def.material() + "' lore has " + lore.size()
                        + " lines, exceeding gui.max-lore-lines (" + maxLoreLines + ") — truncated.");
                lore = new ArrayList<>(lore.subList(0, maxLoreLines));
            }
            meta.lore(Formatter.formatList(lore));

            if (def.customModelData() != null) {
                meta.setCustomModelData(def.customModelData());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public String resolveVariables(String text, GuiSession session, Object loopItem, String iteratorName) {
        if (text == null) return null;

        VariableResolver resolver = plugin.getScriptModule().getVariableResolver();
        GuiExecutionContext context = new GuiExecutionContext(session.getPlayer(), session);
        if (loopItem != null && iteratorName != null) {
            context.setLoopVar(iteratorName, loopItem);
        }

        String processed = text;
        if (loopItem != null) {
            if (iteratorName != null && loopItem instanceof Map<?, ?> map) {
                Pattern pattern = Pattern.compile("\\$" + Pattern.quote(iteratorName) + "\\.([^$]+)\\$");
                Matcher matcher = pattern.matcher(processed);
                StringBuilder sb = new StringBuilder();
                int lastMatch = 0;
                while (matcher.find()) {
                    sb.append(processed, lastMatch, matcher.start());
                    String key = matcher.group(1);
                    Object value = map.get(key);
                    if (value instanceof Number num) {
                        double d = num.doubleValue();
                        if (d == Math.floor(d)) {
                            value = (long) d;
                        }
                    }
                    sb.append(value != null ? value.toString() : matcher.group(0));
                    lastMatch = matcher.end();
                }
                sb.append(processed.substring(lastMatch));
                processed = sb.toString();
            }

            if (iteratorName != null && loopItem instanceof org.nakii.valmora.module.enchant.EnchantmentDefinition enchant) {
                Pattern pattern = Pattern.compile("\\$" + Pattern.quote(iteratorName) + "\\.([^$]+)\\$");
                Matcher matcher = pattern.matcher(processed);
                StringBuilder sb = new StringBuilder();
                int lastMatch = 0;
                while (matcher.find()) {
                    sb.append(processed, lastMatch, matcher.start());
                    String key = matcher.group(1).toLowerCase();
                    Object value = switch (key) {
                        case "id" -> enchant.getId();
                        case "name" -> enchant.getName();
                        case "description" -> String.join("\n", enchant.getDescription());
                        case "etablemaxlevel" -> enchant.getEtableMaxLevel();
                        case "absolutemaxlevel" -> enchant.getAbsoluteMaxLevel();
                        default -> matcher.group(0);
                    };
                    sb.append(value != null ? value.toString() : matcher.group(0));
                    lastMatch = matcher.end();
                }
                sb.append(processed.substring(lastMatch));
                processed = sb.toString();
            }

            processed = processed.replace("$loop_item$", loopItem.toString());
            if (iteratorName != null) {
                processed = processed.replace("$" + iteratorName + "$", loopItem.toString());
            }
        }

        if (!processed.contains("$")) return processed;

        StringBuilder sb = new StringBuilder();
        int lastMatch = 0;
        Pattern pattern = Pattern.compile("\\$([^$]+)\\$");
        Matcher matcher = pattern.matcher(processed);

        while (matcher.find()) {
            sb.append(processed, lastMatch, matcher.start());
            Object resolved = resolver.resolve(matcher.group(1), context);
            if (resolved instanceof Number num) {
                double d = num.doubleValue();
                if (d == Math.floor(d)) {
                    resolved = (long) d;
                }
            }
            sb.append(resolved != null ? resolved.toString() : matcher.group(0));
            lastMatch = matcher.end();
        }
        sb.append(processed.substring(lastMatch));

        return sb.toString();
    }

    public boolean hasNextPage(GuiSession session) {
        // Find all paginated components and check if any has more items than fits on current page
        for (GuiComponent component : session.getDefinition().getComponents().values()) {
            if (component instanceof PaginatedComponent paginated) {
                List<?> items = resolveList(paginated.getListExpression(), session);
                if (items == null) continue;

                int itemsPerPage = (paginated.getPath() != null && !paginated.getPath().isEmpty()) ?
                                    paginated.getPath().length() :
                                    countSlotsForComponent(session.getDefinition(), paginated);

                if (items.size() > (session.getCurrentPage() + 1) * itemsPerPage) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<?> sortList(List<?> items, PaginatedComponent paginated) {
        String order = paginated.getSortOrder();
        if (order == null || order.equals("none")) return items;

        String key = paginated.getSortKey();
        List<Object> mutable = new ArrayList<>(items);

        Comparator<Object> comparator = (a, b) -> {
            String aStr = getSortValue(a, key);
            String bStr = getSortValue(b, key);
            return aStr.compareToIgnoreCase(bStr);
        };
        if (order.equals("desc")) comparator = comparator.reversed();

        try { mutable.sort(comparator); } catch (Exception ignored) {}
        return mutable;
    }

    private String getSortValue(Object item, String key) {
        if (key != null && item instanceof Map<?, ?> map) {
            Object val = map.get(key);
            return val != null ? val.toString() : "";
        }
        return item != null ? item.toString() : "";
    }

    public List<?> resolveList(String path, GuiSession session) {
        if (path == null) return null;
        Object resolved = plugin.getScriptModule().getVariableResolver().resolve(path.replace("$", ""),
            new GuiExecutionContext(session.getPlayer(), session));

        if (resolved instanceof List<?> list) return list;

        if (resolved instanceof String json) {
            String trimmed = json.trim();
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                try {
                    return new Gson().fromJson(trimmed, List.class);
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }
}
