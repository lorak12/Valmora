package org.nakii.valmora.module.modifier;

import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A modifier definition (docs/Valmora_Modifier_Framework_Design.docx §7): display, requirements,
 * state, tiers, and effects. The definition is content data — active attachments on an item are
 * {@link ModifierInstance}s stored via {@link ModifierComponentStore} (§3 core model).
 */
public class ModifierDefinition {

    private final String id;
    private final String groupId;
    private final String displayName;   // may be null
    private final String prefix;        // may be null
    private final String suffix;        // may be null
    private final List<String> lore;    // may be empty
    private final ConditionGroup requirements;
    private final Set<String> tags;
    private final Set<String> conflictIds;
    private final Set<String> conflictTags;
    private final Map<String, ModifierStateDefinition> state; // key -> def
    private final Map<Integer, ModifierTier> tiers; // may be empty -> use baseEffects at tier 1
    private final List<ModifierEffect> baseEffects; // used when tiers is empty
    private final double weight; // relative weight for a group's random-selection application (default 1.0)
    private final Set<ItemType> targetItemTypes; // empty = inherit only the group's restriction

    public ModifierDefinition(String id, String groupId, String displayName, String prefix, String suffix,
                               List<String> lore, ConditionGroup requirements, Set<String> tags,
                               Set<String> conflictIds, Set<String> conflictTags,
                               Map<String, ModifierStateDefinition> state,
                               Map<Integer, ModifierTier> tiers, List<ModifierEffect> baseEffects, double weight,
                               Set<ItemType> targetItemTypes) {
        this.id = id;
        this.groupId = groupId;
        this.displayName = displayName;
        this.prefix = prefix;
        this.suffix = suffix;
        this.lore = lore;
        this.requirements = requirements;
        this.tags = tags;
        this.conflictIds = conflictIds;
        this.conflictTags = conflictTags;
        this.state = state;
        this.tiers = tiers;
        this.baseEffects = baseEffects;
        this.weight = weight;
        this.targetItemTypes = targetItemTypes;
    }

    public String getId() { return id; }
    public String getGroupId() { return groupId; }
    public String getDisplayName() { return displayName; }
    public String getPrefix() { return prefix; }
    public String getSuffix() { return suffix; }
    public List<String> getLore() { return lore; }
    public ConditionGroup getRequirements() { return requirements; }
    public Set<String> getTags() { return tags; }
    public Set<String> getConflictIds() { return conflictIds; }
    public Set<String> getConflictTags() { return conflictTags; }
    public Map<String, ModifierStateDefinition> getState() { return state; }
    public Map<Integer, ModifierTier> getTiers() { return tiers; }
    public double getWeight() { return weight; }
    public Set<ItemType> getTargetItemTypes() { return targetItemTypes; }

    /** Narrows the group's own {@code targets.item_types} — empty means "no additional restriction". */
    public boolean appliesToItemType(ItemType type) {
        return targetItemTypes.isEmpty() || targetItemTypes.contains(type);
    }

    public boolean isTiered() { return !tiers.isEmpty(); }

    public int getMaxTier() {
        return tiers.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    /** Effects for the given tier (falls back to base effects for an untiered modifier, tier clamped to [1, max]). */
    public List<ModifierEffect> getEffects(int tier) {
        if (tiers.isEmpty()) return baseEffects;
        ModifierTier t = tiers.get(Math.max(1, Math.min(tier, getMaxTier())));
        return t != null ? t.getEffects() : baseEffects;
    }

    public String getDisplayName(int tier) {
        if (!tiers.isEmpty()) {
            ModifierTier t = tiers.get(tier);
            if (t != null && t.getDisplayNameOverride() != null) return t.getDisplayNameOverride();
        }
        return displayName;
    }
}
