package org.nakii.valmora.module.gui.components;

import org.nakii.valmora.module.gui.GuiComponent;
import java.util.List;

public final class PaginatedComponent extends GuiComponent {
    private final String listExpression;
    private final boolean destructure;
    private final List<PaginatedState> states;

    public PaginatedComponent(String listExpression, boolean destructure, List<PaginatedState> states) {
        this.listExpression = listExpression;
        this.destructure = destructure;
        this.states = states;
    }

    public String getListExpression() {
        return listExpression;
    }

    public boolean isDestructure() {
        return destructure;
    }

    public List<PaginatedState> getStates() {
        return states;
    }
}
