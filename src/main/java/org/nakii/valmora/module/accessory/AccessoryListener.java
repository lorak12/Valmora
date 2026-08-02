package org.nakii.valmora.module.accessory;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class AccessoryListener implements Listener {

    private final AccessoryModule module;

    public AccessoryListener(AccessoryModule module) {
        this.module = module;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        module.saveAccessories(player, event.getInventory(), holder.getLayout());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder holder)) return;
        AccessoryModule.AccessoryLayout layout = holder.getLayout();

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof AccessoryInventoryHolder;

        if (clickedTop) {
            int slot = event.getSlot();

            // Locked (not-yet-unlocked) filler slot in an item row — no interaction allowed
            if (slot >= layout.itemsOnPage() && slot < layout.controlRowStart()) {
                event.setCancelled(true);
                return;
            }

            // Control row — navigation / close, never accepts items
            if (slot >= layout.controlRowStart()) {
                event.setCancelled(true);
                if (!(event.getWhoClicked() instanceof Player player)) return;
                int rel = slot - layout.controlRowStart();
                if (rel == 0 && layout.page() > 0) {
                    module.openAccessoryBag(player, layout.page() - 1);
                } else if (rel == 8 && layout.page() < layout.totalPages() - 1) {
                    module.openAccessoryBag(player, layout.page() + 1);
                } else if (rel == 4) {
                    player.closeInventory();
                }
                return;
            }

            // Real accessory slot — only accessory items may enter, including shift-click transfers
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && !module.isAccessoryItem(current)) {
                    event.setCancelled(true);
                    return;
                }
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !module.isAccessoryItem(cursor)) {
                event.setCancelled(true);
            }
            return;
        }

        // Clicked in the player's own inventory — block shift-clicking non-accessories up into the bag
        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !module.isAccessoryItem(current)) {
                event.setCancelled(true);
            }
        }
    }
}
