package org.nakii.valmora.module.modifier.value;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;

/**
 * Parses a modifier effect's {@code value:} node (docs/Valmora_Modifier_Framework_Design.docx §18)
 * into a {@link ValueResolver}. Accepts a literal number/numeric string, a {@code { expression:
 * "..." }} map, a {@code { base:, scaling: {...} }} map, or a {@code { resolver: "ns:id" }} map for
 * a Java-registered {@link CustomValue}.
 */
public final class ValueParser {

    private ValueParser() {}

    public static ValueResolver parse(Object raw) {
        if (raw == null) return new LiteralValue(0);
        if (raw instanceof Number n) return new LiteralValue(n.doubleValue());
        if (raw instanceof String s) {
            try {
                return new LiteralValue(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return new ExpressionValue(s);
            }
        }
        if (raw instanceof ConfigurationSection section) return parseSection(section);
        if (raw instanceof Map<?, ?> rawMap) {
            return parseSection(toSection(rawMap));
        }
        return new LiteralValue(0);
    }

    private static ValueResolver parseSection(ConfigurationSection section) {
        if (section.contains("expression")) {
            return new ExpressionValue(section.getString("expression"));
        }
        if (section.contains("resolver")) {
            return new CustomValue(section.getString("resolver"));
        }

        double base = section.getDouble("base", 0);
        ConfigurationSection scaling = section.getConfigurationSection("scaling");
        if (scaling == null) return new LiteralValue(base);

        String type = scaling.getString("type", "RARITY").toUpperCase(Locale.ROOT);
        if (type.equals("CUSTOM")) {
            String resolverId = scaling.contains("resolver") ? scaling.getString("resolver") : scaling.getString("id");
            return new CustomValue(resolverId);
        }

        String property = scaling.getString("property", "power");
        String operation = scaling.getString("operation", "MULTIPLY").toUpperCase(Locale.ROOT);
        double factor = scaling.getDouble("factor", 1.0);
        return new RarityScaleValue(base, property, operation, factor);
    }

    /** Deep-converts a raw {@code Map} (as produced by {@code getMapList("effects")} entries) into a {@link ConfigurationSection}. */
    private static ConfigurationSection toSection(Map<?, ?> rawMap) {
        MemoryConfiguration section = new MemoryConfiguration();
        section.createSection("root", rawMap);
        return section.getConfigurationSection("root");
    }
}
