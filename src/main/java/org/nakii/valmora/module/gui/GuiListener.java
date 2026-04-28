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
import org.nakii.valmora.module.gui.components.*;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.module.recipe.RecipeIngredient;

import java.util.List;
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
        
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // Prevent double-click from stealing items across inventories
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // 1. Click is in the Top Inventory
        if (rawSlot >= 0 && rawSlot < topSize) {
            // Default: Cancel ALL clicks in the top inventory to prevent item theft
            event.setCancelled(true);

            GuiComponent component = getComponentAt(session, rawSlot);
            if (component == null) return;

            if (component instanceof DisplayComponent display) {
                ClickHandler handler = null;
                
                // First check if current state has a handler
                if (!display.getStates().isEmpty()) {
                    GuiRenderer renderer = new GuiRenderer(plugin);
                    PaginatedState state = renderer.findMatchingState(display.getStates(), session, null, null);
                    if (state != null && state.actions() != null) {
                        handler = state.actions().get(event.getClick());
                    }
                }
                
                // Fallback to base actions
                if (handler == null) {
                    handler = display.getActions().get(event.getClick());
                }

                if (handler != null) {
                    executeHandler(session, handler);
                }
            } else if (component instanceof OutputComponent output) {
                handleOutputClick(event, session, output);
            } else if (component instanceof InputComponent) {
                // ALLOW clicks in input slots
                event.setCancelled(false);
                triggerSlotUpdate(session);
            } else if (component instanceof PaginatedComponent paginated) {
                char clickedChar = session.getDefinition().getLayout().get(rawSlot / 9).get(rawSlot % 9);
                handlePaginatedClick(event, session, paginated, rawSlot, clickedChar);
            }else if (component instanceof PageButtonComponent button) {
                handlePageButtonClick(event, session, button);
            }
        } 
        // 2. Click is in Bottom Inventory (Player's inventory)
        else if (event.isShiftClick()) {
            // Block shift-clicking into the GUI to prevent depositing items
            event.setCancelled(true);
        }
    }

   private void handlePaginatedClick(InventoryClickEvent event, GuiSession session, 
                                     PaginatedComponent paginated, int slot, char clickedChar) {
        GuiRenderer renderer = new GuiRenderer(plugin);
        java.util.List<?> items = renderer.resolveList(paginated.getListExpression(), session);
        if (items == null) return;

        int itemsPerPage = (paginated.getPath() != null && !paginated.getPath().isEmpty()) ?
                           paginated.getPath().length() :
                           renderer.countSlotsForComponent(session.getDefinition(), paginated);

        int indexInPage = getPaginatedIndex(session, paginated, slot, clickedChar);
        if (indexInPage == -1) return;

        int itemIndex = session.getCurrentPage() * itemsPerPage + indexInPage;

        if (itemIndex < items.size()) {
            Object loopItem = items.get(itemIndex);
            PaginatedState state = 
                renderer.findMatchingState(paginated.getStates(), session, loopItem, paginated.getIteratorName());
            
            if (state != null && state.actions() != null) {
                ClickHandler handler = state.actions().get(event.getClick());
                if (handler != null) {
                    // Create context and inject the loop item as a param
                    GuiExecutionContext context = new GuiExecutionContext((Player) event.getWhoClicked(), session);
                    if (paginated.getIteratorName() != null) {
                        context.setLoopVar(paginated.getIteratorName(), loopItem);
                    }
                    
                    if (handler.conditions() == null || handler.conditions().evaluate(context)) {
                        if (handler.actions() != null) handler.actions().execute(context);
                    } else {
                        if (handler.failActions() != null) handler.failActions().execute(context);
                    }
                }
            }
        }
    }

    private void handlePageButtonClick(InventoryClickEvent event, GuiSession session, 
                                       PageButtonComponent button) {
        GuiRenderer renderer = new GuiRenderer(plugin);
        if (button.isNext()) {
            if (renderer.hasNextPage(session)) {
                session.setCurrentPage(session.getCurrentPage() + 1);
                renderer.render(session);
            }
        } else {
            if (session.getCurrentPage() > 0) {
                session.setCurrentPage(session.getCurrentPage() - 1);
                renderer.render(session);
            }
        }
    }

    private int getPaginatedIndex(GuiSession session, PaginatedComponent target, int clickedSlot, char clickedChar) {
        if (target.getPath() != null && !target.getPath().isEmpty()) {
            return target.getPath().indexOf(clickedChar);
        }
        java.util.List<java.util.List<Character>> layout = session.getDefinition().getLayout();
        char targetChar = 0;
        for (java.util.Map.Entry<Character, GuiComponent> entry : session.getDefinition().getComponents().entrySet()) {
            if (entry.getValue() == target) {
                targetChar = entry.getKey();
                break;
            }
        }
        
        int counter = 0;
        for (int r = 0; r < layout.size(); r++) {
            java.util.List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                int slot = r * 9 + c;
                if (slot == clickedSlot) return counter;
                if (row.get(c) == targetChar) counter++;
            }
        }
        return -1;
    }

    private void handleOutputClick(InventoryClickEvent event, GuiSession session, OutputComponent output) {
        ItemStack currentOutput = event.getCurrentItem();
        if (currentOutput == null || currentOutput.getType() == Material.AIR) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        org.bukkit.event.inventory.ClickType click = event.getClick();
        String machineId = session.getDefinition().getMachine();
        org.nakii.valmora.module.recipe.RecipeEngine engine = plugin.getRecipeModule().getRecipeEngine();

        boolean massCraft = (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT || 
                             click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);
        
        int craftedCount = 0;
        int maxCrafts = massCraft ? 64 : 1; 
        ItemStack cursor = event.getCursor();

        if (!massCraft) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (!cursor.isSimilar(currentOutput) || cursor.getAmount() + currentOutput.getAmount() > cursor.getMaxStackSize()) {
                    return; 
                }
            }
        }

        // Lock the recipe ID so it doesn't mutate into an accidental slab/hoe during mass crafting
        String initialRecipeId = null;

        while (craftedCount < maxCrafts) {
            Optional<org.nakii.valmora.module.recipe.RecipeDefinition> match = engine.match(machineId, session.getInputSnapshot());
            if (match.isEmpty()) break;
            
            org.nakii.valmora.module.recipe.RecipeDefinition recipe = match.get();

            // Check the lock
            if (initialRecipeId == null) {
                initialRecipeId = recipe.getId();
            } else if (!initialRecipeId.equals(recipe.getId())) {
                break; // The recipe mutated (e.g., ran out of materials and matched a smaller recipe). Abort!
            }

            if (massCraft) {
                if (!canFit(player.getInventory(), currentOutput)) {
                    break; 
                }
                player.getInventory().addItem(currentOutput.clone());
            } else {
                if (cursor == null || cursor.getType() == Material.AIR) {
                    player.setItemOnCursor(currentOutput.clone());
                    cursor = player.getItemOnCursor(); 
                } else {
                    cursor.setAmount(cursor.getAmount() + currentOutput.getAmount());
                }
            }

            if (!recipe.isVanilla()) {
                engine.consume(recipe, session.getInputSnapshot());
            } else {
                consumeVanillaIngredients(session);
            }

            GuiExecutionContext context = new GuiExecutionContext(player, session);
            if (recipe.getOnCraft() != null) recipe.getOnCraft().execute(context);

            craftedCount++;
        }

        if (craftedCount > 0) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> updateRecipeOutput(session));
        }
    }

    // Helper method to protect against over-filling inventory during mass craft loops
    private boolean canFit(org.bukkit.inventory.PlayerInventory inv, ItemStack item) {
        int space = 0;
        for (ItemStack i : inv.getStorageContents()) {
            if (i == null || i.getType() == Material.AIR) {
                space += item.getMaxStackSize();
            } else if (i.isSimilar(item)) {
                space += (item.getMaxStackSize() - i.getAmount());
            }
        }
        return space >= item.getAmount();
    }

    private void consumeVanillaIngredients(GuiSession session) {
        List<List<Character>> layout = session.getDefinition().getLayout();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                char ch = row.get(c);
                GuiComponent comp = session.getDefinition().getComponents().get(ch);
                if (comp instanceof InputComponent) {
                    ItemStack stack = session.getInventory().getItem(r * 9 + c);
                    if (stack != null && stack.getType() != Material.AIR) {
                        stack.setAmount(stack.getAmount() - 1);
                    }
                }
            }
        }
    }

    private void updateRecipeOutput(GuiSession session) {
        String machineId = session.getDefinition().getMachine();
        Optional<RecipeDefinition> match = plugin.getRecipeModule().getRecipeEngine().match(machineId, session.getInputSnapshot());

        int outputSlot = findOutputSlot(session);
        if (outputSlot == -1) return;

        if (match.isPresent()) {
            RecipeDefinition recipe = match.get();

            if (recipe.isVanilla()) {
                ItemStack result = recipe.getVanillaResult();
                if (result != null) {
                    session.getInventory().setItem(outputSlot, result.clone());
                }
                return;
            }

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
        GuiSession session = guiModule.getSession(player.getUniqueId());
        if (session != null && session.getInventory().equals(event.getInventory())) {
            guiModule.closeGuiSession(player);
        }
    }

   @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = guiModule.getSession(player.getUniqueId());
        if (session == null) return;

        boolean inputAffected = false;
        for (int slot : event.getRawSlots()) {
            // Only check slots inside the top GUI (ignore player's own inventory)
            if (slot < event.getInventory().getSize()) {
                GuiComponent component = getComponentAt(session, slot);
                
                // Strict whitelist: Only allow dragging into Input Components
                if (!(component instanceof InputComponent)) {
                    event.setCancelled(true);
                    return;
                } else {
                    inputAffected = true;
                }
            }
        }

        if (inputAffected) {
            triggerSlotUpdate(session);
        }
    }

    private void triggerSlotUpdate(GuiSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateRecipeOutput(session);
            
            GuiEventBlock slotUpdate = session.getDefinition().getOnSlotUpdate();
            if (slotUpdate != null) {
                GuiExecutionContext context = new GuiExecutionContext(session.getPlayer(), session);
                if (slotUpdate.conditions() == null || slotUpdate.conditions().evaluate(context)) {
                    if (slotUpdate.actions() != null) slotUpdate.actions().execute(context);
                } else {
                    if (slotUpdate.failActions() != null) slotUpdate.failActions().execute(context);
                }
            }
            
            new GuiRenderer(plugin).render(session);
        });
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
