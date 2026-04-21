package org.nakii.valmora.module.gui;

import java.util.List;

public record GuiItemStack(
    String material,
    String name,
    List<String> lore,
    int customModelData,
    int amount
) {
    public GuiItemStack {
        if (amount <= 0) amount = 1;
    }
}
