package org.nakii.valmora.module.gui.components;

import org.nakii.valmora.module.gui.GuiComponent;
import java.util.List;

public final class PaginatedComponent extends GuiComponent {
    private final String listExpression;
    private final String iteratorName;
    private final boolean destructure;
    private final List<PaginatedState> states;
    private final String path;

    public PaginatedComponent(String listExpression, String iteratorName, boolean destructure, List<PaginatedState> states, String path) {
        this.listExpression = listExpression;
        this.iteratorName = iteratorName;
        this.destructure = destructure;
        this.states = states;
        this.path = path;
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

    public String getPath() {
        return path;
    }
}
