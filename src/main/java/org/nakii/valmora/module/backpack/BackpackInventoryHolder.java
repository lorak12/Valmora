package org.nakii.valmora.module.backpack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class BackpackInventoryHolder implements InventoryHolder {

    private final Player player;
    private final ItemStack sourceItem; // the backpack item in player's inventory
    private final int sourceSlot;       // inventory slot where the backpack is stored
    private Inventory inventory;

    public BackpackInventoryHolder(Player player, ItemStack sourceItem, int sourceSlot) {
        this.player = player;
        this.sourceItem = sourceItem;
        this.sourceSlot = sourceSlot;
    }

    public Player getPlayer() { return player; }
    public ItemStack getSourceItem() { return sourceItem; }
    public int getSourceSlot() { return sourceSlot; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
