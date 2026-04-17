package org.nakii.valmora.module.gui;

import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Map;

public class GuiDefinition {
    private final String id;
    private final Component title;
    private final int updateIntervalTicks;
    private final int rows;
    private final List<List<Character>> layout;
    private final Map<Character, GuiComponent> components;
    private final GuiEventBlock onOpen;
    private final GuiEventBlock onClose;

    public GuiDefinition(String id, Component title, int updateIntervalTicks, int rows, 
                         List<List<Character>> layout, Map<Character, GuiComponent> components, 
                         GuiEventBlock onOpen, GuiEventBlock onClose) {
        this.id = id;
        this.title = title;
        this.updateIntervalTicks = updateIntervalTicks;
        this.rows = rows;
        this.layout = layout;
        this.components = components;
        this.onOpen = onOpen;
        this.onClose = onClose;
    }

    public String getId() { return id; }
    public Component getTitle() { return title; }
    public int getUpdateIntervalTicks() { return updateIntervalTicks; }
    public int getRows() { return rows; }
    public List<List<Character>> getLayout() { return layout; }
    public Map<Character, GuiComponent> getComponents() { return components; }
    public GuiEventBlock getOnOpen() { return onOpen; }
    public GuiEventBlock getOnClose() { return onClose; }
}
