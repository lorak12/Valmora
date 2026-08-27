package org.nakii.valmora.module.modifier.recipe;

import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.value.ValueResolver;

import java.util.Set;

/**
 * A declarative "modifier anvil" recipe entry (docs/Valmora_Modifier_Framework_Design.docx §16):
 * generalizes the custom-anvil's old gemstone-specific {@code STAT_MODIFIER} operation into
 * {@code APPLY_MODIFIER}/{@code REMOVE_MODIFIER}. The recipe consumes the input item/material and
 * checks costs; {@code ModifierEngine} owns attachment semantics.
 */
public class ModifierRecipeDefinition {

    public enum Operation { APPLY_MODIFIER, REMOVE_MODIFIER }

    private final String id;
    private final String machine;
    private final Operation operation;
    private final Set<ItemType> baseItemTypes; // empty = any
    private final String additionItemId; // null = no addition item required (e.g. a random reroll)
    private final int additionAmount;
    private final String modifierGroup;
    private final String modifierId; // "RANDOM" = weighted random pick; null = REMOVE_MODIFIER wildcard (whole group)
    private final int modifierTier;
    // Cost is a generic value provider (docs §5/§18) — the same rarity-scale/expression/literal
    // pipeline modifier effects use — resolved against the base item's rarity, so e.g. a reforge
    // reroll can cost more for a higher-rarity item without a hardcoded by-rarity cost map.
    private final ValueResolver costXpLevels;
    private final ValueResolver costCoins;

    public ModifierRecipeDefinition(String id, String machine, Operation operation, Set<ItemType> baseItemTypes,
                                     String additionItemId, int additionAmount, String modifierGroup,
                                     String modifierId, int modifierTier, ValueResolver costXpLevels, ValueResolver costCoins) {
        this.id = id;
        this.machine = machine;
        this.operation = operation;
        this.baseItemTypes = baseItemTypes;
        this.additionItemId = additionItemId;
        this.additionAmount = additionAmount;
        this.modifierGroup = modifierGroup;
        this.modifierId = modifierId;
        this.modifierTier = modifierTier;
        this.costXpLevels = costXpLevels;
        this.costCoins = costCoins;
    }

    public String getId() { return id; }
    public String getMachine() { return machine; }
    public Operation getOperation() { return operation; }
    public Set<ItemType> getBaseItemTypes() { return baseItemTypes; }
    public String getAdditionItemId() { return additionItemId; }
    public int getAdditionAmount() { return additionAmount; }
    public String getModifierGroup() { return modifierGroup; }
    public String getModifierId() { return modifierId; }
    public int getModifierTier() { return modifierTier; }
    public ValueResolver getCostXpLevels() { return costXpLevels; }
    public ValueResolver getCostCoins() { return costCoins; }

    public boolean appliesToBase(ItemType type) {
        return baseItemTypes.isEmpty() || baseItemTypes.contains(type);
    }
}
