package org.nakii.valmora.module.stat;

public class StatDefinition {

    private final String id;
    private final String displayName;
    private final double defaultValue;
    private final double maxValue;
    private final String color;
    private final String icon;
    private final String description;
    private final boolean pool;
    private final String vanillaAttribute;

    public StatDefinition(String id, String displayName, double defaultValue, double maxValue,
                          String color, String icon, String description, boolean pool,
                          String vanillaAttribute) {
        this.id = id;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.maxValue = maxValue;
        this.color = color;
        this.icon = icon;
        this.description = description;
        this.pool = pool;
        this.vanillaAttribute = vanillaAttribute;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public double getDefaultValue() { return defaultValue; }
    public double getMaxValue() { return maxValue; }
    public String getColor() { return color; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
    public boolean isPool() { return pool; }
    public String getVanillaAttribute() { return vanillaAttribute; }

    public String getFormattedName() {
        return color + displayName;
    }

    public String format(double value) {
        return color + displayName + ": " + (value >= 0 ? "+" : "") + (int) value;
    }
}
