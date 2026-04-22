package org.nakii.valmora.module.gui.components;

import org.bukkit.event.inventory.ClickType;
import org.nakii.valmora.module.gui.ClickHandler;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;

public final class DisplayComponent extends GuiComponent {
    @Nullable
    private final GuiItemStack displayItem;
    private final Map<ClickType, ClickHandler> actions;
    private final List<PaginatedState> states;

    public DisplayComponent(@Nullable GuiItemStack displayItem, Map<ClickType, ClickHandler> actions, List<PaginatedState> states) {
        this.displayItem = displayItem;
        this.actions = actions;
        this.states = states;
    }

    @Nullable
    public GuiItemStack getDisplayItem() {
        return displayItem;
    }

    public Map<ClickType, ClickHandler> getActions() {
        return actions;
    }

    public List<PaginatedState> getStates() {
        return states;
    }
}
