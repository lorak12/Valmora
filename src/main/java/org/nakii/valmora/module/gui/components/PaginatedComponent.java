package org.nakii.valmora.module.gui.components;

import org.nakii.valmora.module.gui.GuiComponent;
import java.util.List;

public final class PaginatedComponent extends GuiComponent {
    private final String listExpression;
    private final String iteratorName;
    private final boolean destructure;
    private final List<PaginatedState> states;

    public PaginatedComponent(String listExpression, String iteratorName, boolean destructure, List<PaginatedState> states) {
        this.listExpression = listExpression;
        this.iteratorName = iteratorName;
        this.destructure = destructure;
        this.states = states;
    }

    public String getListExpression() {
        return listExpression;
    }

    public String getIteratorName() {
        return iteratorName;
    }

    public boolean isDestructure() {
        return destructure;
    }

    public List<PaginatedState> getStates() {
        return states;
    }
}
