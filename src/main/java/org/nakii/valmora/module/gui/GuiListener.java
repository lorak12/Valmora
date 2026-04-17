package org.nakii.valmora.module.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.gui.components.DisplayComponent;
import org.nakii.valmora.module.gui.components.InputComponent;
import org.nakii.valmora.module.gui.components.OutputComponent;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.module.recipe.RecipeIngredient;
import org.nakii.valmora.module.recipe.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GuiListener implements Listener {

    private final Valmora plugin;
    private final GuiModule guiModule;

    public GuiListener(Valmora plugin, GuiModule guiModule) {
        this.plugin = plugin;
        this.guiModule = guiModule;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        GuiSession session = guiModule.getSession(player.getUniqueId());
        if (session == null) return;
        
        // Block clicks if they affect display items
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < event.getInventory().getSize()) {
            GuiComponent component = getComponentAt(session, slot);
            if (component instanceof DisplayComponent display) {
                event.setCancelled(true);
                ClickHandler handler = display.getActions().get(event.getClick());
                if (handler != null) {
                    executeHandler(session, handler);
                }
            } else if (component instanceof OutputComponent output) {
                handleOutputClick(event, session, output);
            } else if (component instanceof InputComponent) {
                // Schedule a tick delay to re-evaluate recipe after the item actually moves
                Bukkit.getScheduler().runTask(plugin, () -> updateRecipeOutput(session));
            }
        }
    }

    private void handleOutputClick(InventoryClickEvent event, GuiSession session, OutputComponent output) {
        ItemStack currentOutput = event.getCurrentItem();
        if (currentOutput == null || currentOutput.getType() == Material.AIR) {
            event.setCancelled(true);
            return;
        }

        // Re-match to be safe
        Optional<RecipeDefinition> match = plugin.getRecipeModule().getRecipeEngine().match(session.getDefinition().getId(), session.getInputSnapshot());
        if (match.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        RecipeDefinition recipe = match.get();
        event.setCancelled(true);
        
        // Consume ingredients
        consumeIngredients(session, recipe);
        
        // Give output
        event.getWhoClicked().getInventory().addItem(currentOutput.clone());
        
        // Fire on-craft events
        GuiExecutionContext context = new GuiExecutionContext((Player) event.getWhoClicked(), session);
        if (recipe.getOnCraft() != null) recipe.getOnCraft().execute(context);

        // Update preview
        updateRecipeOutput(session);
    }

    private void consumeIngredients(GuiSession session, RecipeDefinition recipe) {
        if (recipe.getType() == RecipeType.EXACT_SLOT || recipe.getType() == RecipeType.SHAPED) {
            Map<String, RecipeIngredient> required = recipe.getInputMap();
            List<List<Character>> layout = session.getDefinition().getLayout();
            for (int r = 0; r < layout.size(); r++) {
                List<Character> row = layout.get(r);
                for (int c = 0; c < row.size(); c++) {
                    char ch = row.get(c);
                    GuiComponent comp = session.getDefinition().getComponents().get(ch);
                    if (comp instanceof InputComponent input && required.containsKey(input.getId())) {
                        ItemStack stack = session.getInventory().getItem(r * 9 + c);
                        if (stack != null) {
                            stack.setAmount(stack.getAmount() - required.get(input.getId()).amount());
                        }
                    }
                }
            }
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            List<RecipeIngredient> required = new ArrayList<>(recipe.getInputList());
            List<List<Character>> layout = session.getDefinition().getLayout();
            
            // Loop over all input slots and consume matching items
            for (int r = 0; r < layout.size(); r++) {
                List<Character> row = layout.get(r);
                for (int c = 0; c < row.size(); c++) {
                    char ch = row.get(c);
                    GuiComponent comp = session.getDefinition().getComponents().get(ch);
                    if (comp instanceof InputComponent) {
                        ItemStack stack = session.getInventory().getItem(r * 9 + c);
                        if (stack == null || stack.getType() == Material.AIR) continue;
                        
                        // Find first matching required ingredient
                        for (int i = 0; i < required.size(); i++) {
                            RecipeIngredient ing = required.get(i);
                            if (isSameItem(stack, ing.item())) {
                                stack.setAmount(stack.getAmount() - ing.amount());
                                required.remove(i);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSameItem(ItemStack stack, String targetId) {
        if (stack == null || targetId == null) return false;
        
        // Check Valmora ID first
        if (stack.hasItemMeta()) {
            String valmoraId = stack.getItemMeta().getPersistentDataContainer().get(org.nakii.valmora.util.Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
            if (valmoraId != null && valmoraId.equalsIgnoreCase(targetId)) return true;
        }
        
        // Fallback to Material
        Material mat = Material.matchMaterial(targetId);
        return mat != null && stack.getType() == mat;
    }

    private void updateRecipeOutput(GuiSession session) {
        Optional<RecipeDefinition> match = plugin.getRecipeModule().getRecipeEngine().match(session.getDefinition().getId(), session.getInputSnapshot());
        
        int outputSlot = findOutputSlot(session);
        if (outputSlot == -1) return;

        if (match.isPresent()) {
            RecipeDefinition recipe = match.get();
            RecipeIngredient firstOutput = recipe.getOutputs().values().iterator().next();
            
            Material mat = Material.matchMaterial(firstOutput.item());
            if (mat == null) {
                ItemStack custom = plugin.getItemManager().createItemStack(firstOutput.item());
                if (custom != null) {
                    custom.setAmount(firstOutput.amount());
                    session.getInventory().setItem(outputSlot, custom);
                    return;
                }
                mat = Material.BARRIER;
            }
            session.getInventory().setItem(outputSlot, new ItemStack(mat, firstOutput.amount()));
        } else {
            session.getInventory().setItem(outputSlot, null);
        }
    }

    private int findOutputSlot(GuiSession session) {
        List<List<Character>> layout = session.getDefinition().getLayout();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                char ch = row.get(c);
                if (session.getDefinition().getComponents().get(ch) instanceof OutputComponent) {
                    return r * 9 + c;
                }
            }
        }
        return -1;
    }

    private void executeHandler(GuiSession session, ClickHandler handler) {
        GuiExecutionContext context = new GuiExecutionContext(session.getPlayer(), session);
        if (handler.conditions() == null || handler.conditions().evaluate(context)) {
            if (handler.actions() != null) handler.actions().execute(context);
        } else {
            if (handler.failActions() != null) handler.failActions().execute(context);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        guiModule.closeGuiSession(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = guiModule.getSession(player.getUniqueId());
        if (session == null) return;

        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                GuiComponent component = getComponentAt(session, slot);
                if (component instanceof DisplayComponent) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private GuiComponent getComponentAt(GuiSession session, int slot) {
        int row = slot / 9;
        int col = slot % 9;
        List<List<Character>> layout = session.getDefinition().getLayout();
        if (row >= layout.size()) return null;
        char c = layout.get(row).get(col);
        return session.getDefinition().getComponents().get(c);
    }
}
