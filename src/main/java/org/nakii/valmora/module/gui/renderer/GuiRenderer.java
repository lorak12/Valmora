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
import org.nakii.valmora.util.Formatter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiRenderer {

    private final Valmora plugin;
    private final Map<PaginatedComponent, Integer> paginatedCounters = new HashMap<>();

    public GuiRenderer(Valmora plugin) {
        this.plugin = plugin;
    }

    public void render(GuiSession session) {
        Inventory inv = session.getInventory();

        Map<Integer, ItemStack> savedInputItems = saveInputItems(session);
        Map<Integer, ItemStack> savedOutputItems = saveOutputItems(session);

        inv.clear();
        this.paginatedCounters.clear();

        renderLayout(session, inv);

        restoreInputItems(session, savedInputItems);
        updateOutputSlot(session, savedOutputItems);
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

        for (OutputComponent output : getOutputComponents(def)) {
            for (int slot : findAllSlotsForComponent(def, output)) {
                Optional<RecipeDefinition> match = matchRecipe(session);
                if (match.isPresent()) {
                    RecipeDefinition recipe = match.get();
                    RecipeIngredient firstOutput = recipe.getOutputs().values().iterator().next();

                    Material mat = Material.matchMaterial(firstOutput.item());
                    if (mat == null) {
                        ItemStack custom = plugin.getItemManager().createItemStack(firstOutput.item());
                        if (custom != null) {
                            custom.setAmount(firstOutput.amount());
                            inv.setItem(slot, custom);
                            continue;
                        }
                        mat = Material.BARRIER;
                    }
                    inv.setItem(slot, new ItemStack(mat, firstOutput.amount()));
                } else {
                    inv.setItem(slot, null);
                }
            }
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
                if (plugin.getScriptModule().getConditionParser().parse(condStr).evaluate(context)) {
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
            meta.lore(Formatter.formatList(lore));

            if (def.customModelData() != 0) {
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
