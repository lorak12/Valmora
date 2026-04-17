package org.nakii.valmora.module.gui.components;

import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiItemStack;

public final class PageButtonComponent extends GuiComponent {
    private final boolean isNext;
    private final GuiItemStack displayItem;
    private final GuiItemStack fallbackItem;

    public PageButtonComponent(boolean isNext, GuiItemStack displayItem, GuiItemStack fallbackItem) {
        this.isNext = isNext;
        this.displayItem = displayItem;
        this.fallbackItem = fallbackItem;
    }

    public boolean isNext() {
        return isNext;
    }

    public GuiItemStack getDisplayItem() {
        return displayItem;
    }

    public GuiItemStack getFallbackItem() {
        return fallbackItem;
    }
}
