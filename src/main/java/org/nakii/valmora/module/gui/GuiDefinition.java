package org.nakii.valmora.module.gui;

import java.util.List;
import java.util.Map;

public class GuiDefinition {
    private final String id;
    private final String title;
    private final int updateIntervalTicks;
    private final int rows;
    private final String machine;
    private final List<List<Character>> layout;
    private final Map<Character, GuiComponent> components;
    private final GuiEventBlock onOpen;
    private final GuiEventBlock onClose;

    public GuiDefinition(String id, String title, int updateIntervalTicks, int rows,
                         String machine,
                         List<List<Character>> layout, Map<Character, GuiComponent> components,
                         GuiEventBlock onOpen, GuiEventBlock onClose) {
        this.id = id;
        this.title = title;
        this.updateIntervalTicks = updateIntervalTicks;
        this.rows = rows;
        this.machine = machine;
        this.layout = layout;
        this.components = components;
        this.onOpen = onOpen;
        this.onClose = onClose;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getUpdateIntervalTicks() { return updateIntervalTicks; }
    public int getRows() { return rows; }
    public String getMachine() { return machine; }
    public List<List<Character>> getLayout() { return layout; }
    public Map<Character, GuiComponent> getComponents() { return components; }
    public GuiEventBlock getOnOpen() { return onOpen; }
    public GuiEventBlock getOnClose() { return onClose; }
}
