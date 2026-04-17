package org.nakii.valmora.module.gui;

import org.bukkit.Material;
import java.util.List;

public record GuiItemStack(
    Material material,
    String name,
    List<String> lore,
    int customModelData,
    int amount
) {
    public GuiItemStack {
        if (amount <= 0) amount = 1;
    }
}
