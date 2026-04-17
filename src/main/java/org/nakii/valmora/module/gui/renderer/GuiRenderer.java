package org.nakii.valmora.module.gui.renderer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.gui.*;
import org.nakii.valmora.module.gui.components.*;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;

public class GuiRenderer {

    private final Valmora plugin;

    public GuiRenderer(Valmora plugin) {
        this.plugin = plugin;
    }

    public void render(GuiSession session) {
        GuiDefinition def = session.getDefinition();
        Inventory inv = session.getInventory();
        inv.clear();
        this.paginatedItemCounter = 0;

        List<List<Character>> layout = def.getLayout();
        for (int row = 0; row < layout.size(); row++) {
            List<Character> rowChars = layout.get(row);
            for (int col = 0; col < rowChars.size(); col++) {
                char c = rowChars.get(col);
                int slot = row * 9 + col;
                if (slot >= inv.getSize()) break;

                GuiComponent component = def.getComponents().get(c);
                if (component instanceof DisplayComponent display) {
                    inv.setItem(slot, createItemStack(display.getDisplayItem(), session, null));
                } else if (component instanceof PaginatedComponent paginated) {
                    renderPaginatedSlot(session, inv, slot, paginated);
                } else if (component instanceof PageButtonComponent button) {
                    renderPageButton(session, inv, slot, button);
                }
            }
        }
    }

    private int paginatedItemCounter = 0;

    private void renderPaginatedSlot(GuiSession session, Inventory inv, int slot, PaginatedComponent paginated) {
        List<?> items = resolveList(paginated.getListExpression(), session);
        if (items == null) return;

        int itemsPerPage = countSlotsForComponent(session.getDefinition(), paginated);
        int itemIndex = session.getCurrentPage() * itemsPerPage + paginatedItemCounter;
        
        if (itemIndex < items.size()) {
            Object loopItem = items.get(itemIndex);
            PaginatedState state = findMatchingState(paginated.getStates(), session, loopItem);
            if (state != null) {
                inv.setItem(slot, createItemStack(state.displayItem(), session, loopItem));
            }
            paginatedItemCounter++;
        }
    }

    private void renderPageButton(GuiSession session, Inventory inv, int slot, PageButtonComponent button) {
        boolean hasNext = hasNextPage(session);
        boolean hasPrev = session.getCurrentPage() > 0;
        
        boolean active = button.isNext() ? hasNext : hasPrev;
        GuiItemStack itemDef = active ? button.getDisplayItem() : button.getFallbackItem();
        
        if (itemDef != null) {
            inv.setItem(slot, createItemStack(itemDef, session, null));
        }
    }

    public ItemStack createItemStack(GuiItemStack def, GuiSession session, Object loopItem) {
        if (def == null) return null;
        ItemStack item = new ItemStack(def.material(), def.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = resolveVariables(def.name(), session, loopItem);
            meta.displayName(Formatter.format(name));

            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) {
                lore.add(resolveVariables(line, session, loopItem));
            }
            meta.lore(Formatter.formatList(lore));

            if (def.customModelData() != 0) {
                meta.setCustomModelData(def.customModelData());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String resolveVariables(String text, GuiSession session, Object loopItem) {
        if (text == null) return null;
        
        VariableResolver resolver = plugin.getScriptModule().getVariableResolver();
        GuiExecutionContext context = new GuiExecutionContext(session.getPlayer(), session);
        // If loopItem is present, we should ideally inject it into the context
        // But for now let's use a simple replacement if available
        
        String processed = text;
        if (loopItem != null) {
            // Simplified destructuring: if loopItem is a Map or has fields
            // For now let's just support $loop_item$ as the object itself
            processed = processed.replace("$loop_item$", loopItem.toString());
        }
        
        if (!processed.contains("$")) return processed;
        
        StringBuilder sb = new StringBuilder();
        int lastMatch = 0;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$([^$]+)\\$");
        java.util.regex.Matcher matcher = pattern.matcher(processed);
        
        while (matcher.find()) {
            sb.append(processed, lastMatch, matcher.start());
            Object resolved = resolver.resolve(matcher.group(1), context);
            sb.append(resolved != null ? resolved.toString() : matcher.group(0));
            lastMatch = matcher.end();
        }
        sb.append(processed.substring(lastMatch));
        
        return sb.toString();
    }

    private List<?> resolveList(String path, GuiSession session) {
        if (path == null) return null;
        Object resolved = plugin.getScriptModule().getVariableResolver().resolve(path.replace("$", ""), 
            new GuiExecutionContext(session.getPlayer(), session));
        if (resolved instanceof List<?> list) return list;
        return null;
    }

    private int countSlotsForComponent(GuiDefinition def, PaginatedComponent target) {
        int count = 0;
        for (List<Character> row : def.getLayout()) {
            for (char c : row) {
                if (def.getComponents().get(c) == target) count++;
            }
        }
        return count;
    }

    private PaginatedState findMatchingState(List<PaginatedState> states, GuiSession session, Object loopItem) {
        for (PaginatedState state : states) {
            if (state.condition().equalsIgnoreCase("default")) return state;
            
            // For now, since we don't have a sub-context provider for loop items, 
            // we'll treat the condition as a string equality check if it's not a complex condition.
            // In a full implementation, we'd register a temporary "loop_item" provider.
            if (state.condition().equalsIgnoreCase(loopItem.toString())) return state;
        }
        return null;
    }

    private boolean hasNextPage(GuiSession session) {
        // Logic to check if more items exist
        return false; 
    }
}
