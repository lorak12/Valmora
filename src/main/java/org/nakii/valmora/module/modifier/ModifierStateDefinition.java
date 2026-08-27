package org.nakii.valmora.module.modifier;

/**
 * Declares one modifier-local state field (docs/Valmora_Modifier_Framework_Design.docx §13), e.g.
 * {@code souls: { type: INTEGER, default: 0, min: 0, max: 100 }}.
 */
public class ModifierStateDefinition {

    private final String key;
    private final int defaultValue;
    private final int min;
    private final int max;

    public ModifierStateDefinition(String key, int defaultValue, int min, int max) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public String getKey() { return key; }
    public int getDefaultValue() { return defaultValue; }
    public int getMin() { return min; }
    public int getMax() { return max; }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}
