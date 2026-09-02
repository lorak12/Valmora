package org.nakii.valmora.module.gui;

import java.util.List;

public record GuiItemStack(
    String material,
    String name,
    List<String> lore,
    // HC-164 fix: nullable instead of an int defaulting to 0 as a "not set" sentinel — 0 is a
    // valid custom-model-data value in many resource packs, so the old sentinel silently ate a
    // legitimate CMD-0 icon whenever `custom-model-data: 0` was written explicitly.
    Integer customModelData,
    int amount
) {
    public GuiItemStack {
        // HC-165 fix: an author-typo'd non-positive amount used to be silently clamped to 1 with
        // no signal anything was wrong — now logged once at construction (item load time).
        if (amount <= 0) {
            org.nakii.valmora.Valmora plugin = org.nakii.valmora.Valmora.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("[GUI] Item stack amount " + amount + " <= 0 for material '"
                        + material + "' — clamped to 1. Check the GUI YAML's `amount:` field.");
            }
            amount = 1;
        }
    }
}
