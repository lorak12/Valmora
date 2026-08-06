package org.nakii.valmora.module.gui.storage;

import org.bukkit.inventory.ItemStack;

/**
 * Points at the physical origin of an item-bound {@code StorageComponent}'s carrier item —
 * either a slot in the player's own inventory (opened directly via a right-click ability) or
 * a slot inside another GUI's inventory (opened as a nested container, e.g. a backpack sitting
 * inside an accessory bag's storage slot).
 */
public interface ItemBindingHandle {
    ItemStack getItem();
    void writeBack(ItemStack item);
}
