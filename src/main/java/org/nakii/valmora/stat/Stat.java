package org.nakii.valmora;

public enum Stat {
    HEALTH("Health", 100.0, Double.MAX_VALUE),
    STRENGTH("Strength", 10.0, Double.MAX_VALUE),
    DEFENSE("Defense", 10.0, Double.MAX_VALUE),
    SPEED("Speed", 100.0, Double.MAX_VALUE);

    private final String displayName;
    private final double defaultValue;
    private final double maxValue;

    Stat(String displayName, double defaultValue, double maxValue) {
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.maxValue = maxValue;
    }

    public String getDisplayName() {
        return displayName;
    }
    public double getDefaultValue(Stat speed) {
        return defaultValue;
    }
    public double getMaxValue() {
        return maxValue;
    }


}
