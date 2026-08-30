package org.nakii.valmora.module.gui;

import org.nakii.valmora.module.gui.components.OutputComponent;

import java.util.LinkedHashMap;
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
    private final GuiEventBlock onSlotUpdate;
    private final GuiEventBlock onUpdate;
    private final String command;
    private final String commandPermission;

    public GuiDefinition(String id, String title, int updateIntervalTicks, int rows,
                         String machine,
                         List<List<Character>> layout, Map<Character, GuiComponent> components,
                         GuiEventBlock onOpen, GuiEventBlock onClose, GuiEventBlock onSlotUpdate,
                         GuiEventBlock onUpdate, String command, String commandPermission) {
        this.id = id;
        this.title = title;
        this.updateIntervalTicks = updateIntervalTicks;
        this.rows = rows;
        this.machine = machine;
        this.layout = layout;
        this.components = components;
        this.onOpen = onOpen;
        this.onClose = onClose;
        this.onSlotUpdate = onSlotUpdate;
        this.onUpdate = onUpdate;
        this.command = command;
        this.commandPermission = commandPermission;
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
    public GuiEventBlock getOnSlotUpdate() { return onSlotUpdate; }
    public GuiEventBlock getOnUpdate() { return onUpdate; }
    public String getCommand() { return command; }
    public String getCommandPermission() { return commandPermission; }

    /**
     * Maps every {@link OutputComponent}'s {@code id:} to its first physical slot index (row*9+col,
     * layout scan order) — used to route a recipe's {@code outputs:} entries to the correct physical
     * GUI slot by {@code slot:} (see {@code RecipeOutput}/CLAUDE.md's recipe-outputs routing
     * convention: a recipe with more than one output must name which OUTPUT component id each one
     * goes to). Iteration order matches first-encountered layout position, so a single-output GUI's
     * sole entry is reachable positionally too when a recipe output omits {@code slot:}.
     */
    public Map<String, Integer> findOutputSlotsById() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                GuiComponent comp = components.get(row.get(c));
                if (comp instanceof OutputComponent output) {
                    result.putIfAbsent(output.getId(), r * 9 + c);
                }
            }
        }
        return result;
    }
}
