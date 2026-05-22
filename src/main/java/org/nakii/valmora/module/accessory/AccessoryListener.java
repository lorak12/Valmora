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
        module.saveAccessories(player, event.getInventory());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder)) return;
        // Only allow accessories in the bag — reject non-accessory items being placed in
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && !module.isAccessoryItem(cursor)) {
            event.setCancelled(true);
        }
    }
}
