package org.nakii.valmora.module.item;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.PlayerProfileLoadedEvent;

/**
 * The moments existing items are brought up to date ({@link ItemRefresher}): when a player's
 * profile (and inventory) loads, when they open a real container, pick an item up, or switch held
 * item. Together with the post-reload pass in ModuleManager, this reaches every item a player can
 * see, without scanning worlds.
 */
public class ItemRefreshListener implements Listener {

    private final Valmora plugin;

    public ItemRefreshListener(Valmora plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProfileLoaded(PlayerProfileLoadedEvent event) {
        Player player = Bukkit.getPlayer(event.getUuid());
        if (player != null) ItemRefresher.refresh(plugin, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory top = event.getInventory();
        // Only real storage (chests, barrels, shulkers, donkeys, ender chests, ...). Plugin menus,
        // including Valmora's own GUIs, show display items that must not be rewritten.
        InventoryHolder holder = top.getHolder(false);
        boolean realStorage = top.getType() == InventoryType.ENDER_CHEST
                || holder instanceof org.bukkit.block.Container
                || holder instanceof org.bukkit.block.DoubleChest
                || (holder instanceof Entity && !(holder instanceof Player));
        if (realStorage) ItemRefresher.refresh(plugin, top);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemRefresher.refresh(plugin, event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack(); // a copy — write it back if it changed
        if (ItemRefresher.refresh(plugin, stack)) entity.setItemStack(stack);
    }
}
