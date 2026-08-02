package org.nakii.valmora.module.accessory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;

public class AccessoryInventoryHolder implements InventoryHolder {

    private final Player player;
    private final AccessoryModule.AccessoryLayout layout;
    private Inventory inventory;

    public AccessoryInventoryHolder(Player player, AccessoryModule.AccessoryLayout layout) {
        this.player = player;
        this.layout = layout;
    }

    public Player getPlayer() { return player; }

    public AccessoryModule.AccessoryLayout getLayout() { return layout; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
