package org.nakii.valmora.module.gui.components;

import org.bukkit.event.inventory.ClickType;
import org.nakii.valmora.module.gui.ClickHandler;
import org.nakii.valmora.module.gui.GuiItemStack;
import java.util.Map;

public record PaginatedState(
    String condition,
    GuiItemStack displayItem,
    Map<ClickType, ClickHandler> actions
) {
}
