package org.nakii.valmora.module.stat;

public enum Stat {
    DAMAGE("Damage", 5.0, Double.MAX_VALUE, "<red>"),
    HEALTH("Health", 100.0, Double.MAX_VALUE, "<red>"),
    STRENGTH("Strength", 0.0, Double.MAX_VALUE, "<red>"),
    DEFENSE("Defense", 0.0, Double.MAX_VALUE, "<green>"),
    CRIT_CHANCE("Crit Chance", 30.0, 100.0, "<yellow>"),
    CRIT_DAMAGE("Crit Damage", 50.0, Double.MAX_VALUE, "<yellow>"),
    SPEED("Speed", 100.0, Double.MAX_VALUE, "<white>"),
    MANA("Mana", 100.0, Double.MAX_VALUE, "<aqua>"),
    HEALTH_REGEN("Health Regen", 1.0, Double.MAX_VALUE, "<red>"),
    MANA_REGEN("Mana Regen", 2.0, Double.MAX_VALUE, "<aqua>"),
    LUCK("Luck", 0.0, 100.0, "<gold>");

    private final String displayName;
    private final double defaultValue;
    private final double maxValue;
    private final String color;

    Stat(String displayName, double defaultValue, double maxValue, String color) {
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.maxValue = maxValue;
        this.color = color;
    }

    public String getDisplayName() {
        return color + displayName;
    }
    public double getDefaultValue() {
        return defaultValue;
    }
    public double getMaxValue() {
        return maxValue;
    }

    public String format(double value) {
        return color + displayName + ": " + (value > 0 ? "+" : "-") + value;
    }
}
