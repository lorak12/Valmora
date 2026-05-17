package org.nakii.valmora.module.npc;

import java.util.List;

public class HologramDefinition {
    private final String name;
    private final String text;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final List<String> conditions;
    private final int checkInterval;

    public HologramDefinition(String name, String text,
                               double offsetX, double offsetY, double offsetZ,
                               List<String> conditions, int checkInterval) {
        this.name = name;
        this.text = text;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.conditions = List.copyOf(conditions);
        this.checkInterval = Math.max(1, checkInterval);
    }

    public String getName() { return name; }
    public String getText() { return text; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public List<String> getConditions() { return conditions; }
    public int getCheckInterval() { return checkInterval; }
}
