package org.nakii.valmora.module.accessory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;

public class AccessoryInventoryHolder implements InventoryHolder {

    private final Player player;
    private Inventory inventory;

    public AccessoryInventoryHolder(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
