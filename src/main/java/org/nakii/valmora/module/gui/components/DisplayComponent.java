package org.nakii.valmora.module.gui.components;

import org.bukkit.event.inventory.ClickType;
import org.nakii.valmora.module.gui.ClickHandler;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiItemStack;
import java.util.Map;

public final class DisplayComponent extends GuiComponent {
    private final GuiItemStack displayItem;
    private final Map<ClickType, ClickHandler> actions;

    public DisplayComponent(GuiItemStack displayItem, Map<ClickType, ClickHandler> actions) {
        this.displayItem = displayItem;
        this.actions = actions;
    }

    public GuiItemStack getDisplayItem() {
        return displayItem;
    }

    public Map<ClickType, ClickHandler> getActions() {
        return actions;
    }
}
