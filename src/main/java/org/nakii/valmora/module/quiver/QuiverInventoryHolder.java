package org.nakii.valmora.module.quiver;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class QuiverInventoryHolder implements InventoryHolder {

    private final Player player;
    private Inventory inventory;

    public QuiverInventoryHolder(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
