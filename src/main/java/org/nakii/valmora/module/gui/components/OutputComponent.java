package org.nakii.valmora.module.gui.components;

import org.nakii.valmora.module.gui.GuiComponent;

public final class OutputComponent extends GuiComponent {
    private final String id;

    public OutputComponent(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
