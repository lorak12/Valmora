package org.nakii.valmora.module.gui.storage;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.gui.GuiSession;

/** Binds to an item sitting inside another GUI's inventory slot (nested container open). */
public class GuiSlotItemHandle implements ItemBindingHandle {

    private final GuiSession originSession;
    private final int originSlot;

    public GuiSlotItemHandle(GuiSession originSession, int originSlot) {
        this.originSession = originSession;
        this.originSlot = originSlot;
    }

    @Override
    public ItemStack getItem() {
        return originSession.getInventory().getItem(originSlot);
    }

    @Override
    public void writeBack(ItemStack item) {
        originSession.getInventory().setItem(originSlot, item);
    }
}
