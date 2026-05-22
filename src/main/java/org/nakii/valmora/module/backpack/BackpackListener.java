package org.nakii.valmora.module.backpack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class BackpackListener implements Listener {

    private final BackpackModule module;

    public BackpackListener(BackpackModule module) {
        this.module = module;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackInventoryHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Save contents back to the source backpack item
        module.saveContents(holder.getSourceItem(), event.getInventory());

        // Update the item in the player's inventory to reflect saved state
        player.getInventory().setItem(holder.getSourceSlot(), holder.getSourceItem());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackInventoryHolder)) return;

        // Prevent placing backpacks inside backpacks (nesting)
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && module.isBackpack(cursor)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(org.nakii.valmora.util.Formatter.format(
                        "<red>You cannot place a backpack inside another backpack."));
            }
        }
    }
}
