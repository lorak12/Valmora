package org.nakii.valmora.module.gui.storage;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Binds to an item sitting directly in a player's own inventory (top-level container open). */
public class PlayerHeldItemHandle implements ItemBindingHandle {

    private final Player player;
    private final int slot;

    public PlayerHeldItemHandle(Player player, int slot) {
        this.player = player;
        this.slot = slot;
    }

    @Override
    public ItemStack getItem() {
        return player.getInventory().getItem(slot);
    }

    @Override
    public void writeBack(ItemStack item) {
        player.getInventory().setItem(slot, item);
    }
}
