package org.nakii.valmora.module.hud;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;

public class HudItemDefinition {

    private final String id;
    private final int slot;
    private final boolean preventMove;
    private final ItemStack item;
    private final CompiledEvent onRightClick;
    private final CompiledEvent onLeftClick;

    public HudItemDefinition(String id, int slot, boolean preventMove, ItemStack item,
                              CompiledEvent onRightClick, CompiledEvent onLeftClick) {
        this.id = id;
        this.slot = slot;
        this.preventMove = preventMove;
        this.item = item;
        this.onRightClick = onRightClick;
        this.onLeftClick = onLeftClick;
    }

    public String getId() { return id; }
    public int getSlot() { return slot; }
    public boolean isPreventMove() { return preventMove; }
    public ItemStack getItem() { return item.clone(); }
    public CompiledEvent getOnRightClick() { return onRightClick; }
    public CompiledEvent getOnLeftClick() { return onLeftClick; }
}
