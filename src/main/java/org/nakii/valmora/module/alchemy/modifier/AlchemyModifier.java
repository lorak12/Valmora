package org.nakii.valmora.module.alchemy.modifier;

/**
 * Represents a single item that modifies an existing Valmora alchemy potion.
 *
 * Configuration is loaded from alchemy/modifiers.yml.
 * Item IDs use the "minecraft:material_name" or "valmora_item_id" convention.
 *
 * requiresMaxBase — when true, the modifier can only be applied to a potion
 * whose current level equals the effect's maximum BASE level (i.e. the highest
 * level reachable through base recipes alone, without any level modifier).
 */
public class AlchemyModifier {

    private final String itemId;
    private final AlchemyModifierType type;

    // LEVEL modifier
    private final int levelBonus;

    // DURATION modifier (absolute, in seconds)
    private final int durationSeconds;

    // SPLASH modifier (multiplied against the current duration at apply time)
    private final double durationMultiplier;

    private final boolean requiresMaxBase;

    public AlchemyModifier(String itemId, AlchemyModifierType type,
                           int levelBonus, int durationSeconds,
                           double durationMultiplier, boolean requiresMaxBase) {
        this.itemId = itemId;
        this.type = type;
        this.levelBonus = levelBonus;
        this.durationSeconds = durationSeconds;
        this.durationMultiplier = durationMultiplier;
        this.requiresMaxBase = requiresMaxBase;
    }

    public String getItemId() { return itemId; }
    public AlchemyModifierType getType() { return type; }
    public int getLevelBonus() { return levelBonus; }
    public int getDurationSeconds() { return durationSeconds; }
    public double getDurationMultiplier() { return durationMultiplier; }
    public boolean isRequiresMaxBase() { return requiresMaxBase; }
}
