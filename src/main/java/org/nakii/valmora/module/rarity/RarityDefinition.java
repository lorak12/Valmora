package org.nakii.valmora.module.rarity;

import java.util.Collections;
import java.util.Map;

/**
 * Fully data-driven rarity metadata, loaded from {@code rarities.yml} (see CLAUDE.md §Rarities and
 * docs/Valmora_Modifier_Framework_Design.docx §4). Immutable value object.
 *
 * <p>{@code key} is the enum-style identifier used to key this definition in {@link RarityRegistry}
 * and matches {@link org.nakii.valmora.module.item.Rarity}'s enum constant name (e.g. {@code
 * "LEGENDARY"}), so the legacy on-item PDC representation and this new metadata layer stay in sync
 * without requiring a full migration of every {@code Rarity} enum usage site in one pass.
 */
public class RarityDefinition {

    private final String key;
    private final String id;
    private final String name;
    private final String color;
    private final int rank;
    private final double power;
    private final Map<String, Double> extra; // any other numeric key under this rarity's YAML entry

    public RarityDefinition(String key, String id, String name, String color, int rank, double power) {
        this(key, id, name, color, rank, power, Collections.emptyMap());
    }

    /**
     * @param extra arbitrary additional numeric properties (e.g. {@code forge_cost}, {@code
     *              sell_multiplier}) — content authors can add any key to a rarity's YAML entry and
     *              reference it from a modifier/recipe's {@code scaling: { property: <key> }}
     *              without an engine change (§4's "Optional future metadata" note).
     */
    public RarityDefinition(String key, String id, String name, String color, int rank, double power, Map<String, Double> extra) {
        this.key = key;
        this.id = id;
        this.name = name;
        this.color = color;
        this.rank = rank;
        this.power = power;
        this.extra = extra;
    }

    /** The registry key (enum-constant-style, e.g. {@code "LEGENDARY"}). */
    public String getKey() { return key; }

    /** Stable machine identifier used by items/APIs (e.g. {@code "legendary"}). */
    public String getId() { return id; }

    public String getName() { return name; }

    public String getColor() { return color; }

    /** Ordered rarity position (0 = weakest) for comparisons and linear ({@code rank}-based) scaling. */
    public int getRank() { return rank; }

    /** Content-authoring scale factor consumed by the default {@code RARITY} modifier value provider. */
    public double getPower() { return power; }

    /**
     * Reads a named numeric property off this rarity for generic value resolution
     * (docs/Valmora_Modifier_Framework_Design.docx §5). Returns {@code Double.NaN} if unknown.
     */
    public double getProperty(String property) {
        if (property == null) return Double.NaN;
        String key = property.toLowerCase(java.util.Locale.ROOT);
        return switch (key) {
            case "rank" -> rank;
            case "power" -> power;
            default -> extra.getOrDefault(key, Double.NaN);
        };
    }
}
