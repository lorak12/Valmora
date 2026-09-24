package org.nakii.valmora.module.modifier;

import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
        ModifierTier t = tierAtOrBelow(tier);
        return t != null ? t.getEffects() : baseEffects;
    }

    public String getDisplayName(int tier) {
        if (!tiers.isEmpty()) {
            ModifierTier t = tierAtOrBelow(tier);
            if (t != null && t.getDisplayNameOverride() != null) return t.getDisplayNameOverride();
        }
        return displayName;
    }

    /**
     * The highest declared tier at or below {@code tier} — so a reforge that only declares tiers
     * 1, 3 and 5 gives an EPIC (tier 4) item tier 3's effects, instead of the top-level
     * {@code effects:} (usually empty). Same "next lower defined tier" rule the pre-modifier
     * reforge system had for its per-rarity tables.
     */
    private ModifierTier tierAtOrBelow(int tier) {
        for (int t = Math.max(1, Math.min(tier, getMaxTier())); t >= 1; t--) {
            ModifierTier found = tiers.get(t);
            if (found != null) return found;
        }
        return null;
    }

    /** Fluent Java construction (docs/Valmora_Modifier_Framework_Design.docx §20) for a plugin registering a custom modifier without YAML. */
    public static Builder builder(String id, String groupId) {
        return new Builder(id, groupId);
    }

    public static class Builder {
        private final String id;
        private final String groupId;
        private String displayName;
        private String prefix;
        private String suffix;
        private List<String> lore = List.of();
        private ConditionGroup requirements = new ConditionGroup(Collections.emptyList());
        private final Set<String> tags = new HashSet<>();
        private final Set<String> conflictIds = new HashSet<>();
        private final Set<String> conflictTags = new HashSet<>();
        private final Map<String, ModifierStateDefinition> state = new HashMap<>();
        private final Map<Integer, ModifierTier> tiers = new HashMap<>();
        private List<ModifierEffect> baseEffects = new java.util.ArrayList<>();
        private double weight = 1.0;
        private final Set<ItemType> targetItemTypes = new HashSet<>();

        private Builder(String id, String groupId) { this.id = id; this.groupId = groupId; }

        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder prefix(String prefix) { this.prefix = prefix; return this; }
        public Builder suffix(String suffix) { this.suffix = suffix; return this; }
        public Builder lore(List<String> lore) { this.lore = lore; return this; }
        public Builder requirements(ConditionGroup requirements) { this.requirements = requirements; return this; }
        public Builder tag(String tag) { this.tags.add(tag); return this; }
        public Builder conflictId(String modifierId) { this.conflictIds.add(modifierId); return this; }
        public Builder conflictTag(String tag) { this.conflictTags.add(tag); return this; }
        public Builder state(String key, ModifierStateDefinition def) { this.state.put(key, def); return this; }
        public Builder tier(int tier, ModifierTier def) { this.tiers.put(tier, def); return this; }
        public Builder effect(ModifierEffect effect) { this.baseEffects.add(effect); return this; }
        public Builder effects(List<ModifierEffect> effects) { this.baseEffects = new java.util.ArrayList<>(effects); return this; }
        public Builder weight(double weight) { this.weight = weight; return this; }
        public Builder targetItemType(ItemType type) { this.targetItemTypes.add(type); return this; }
        public Builder targetItemTypes(Set<ItemType> types) { this.targetItemTypes.addAll(types); return this; }

        public ModifierDefinition build() {
            return new ModifierDefinition(id, groupId, displayName, prefix, suffix, lore, requirements,
                    Set.copyOf(tags), Set.copyOf(conflictIds), Set.copyOf(conflictTags), Map.copyOf(state),
                    Map.copyOf(tiers), List.copyOf(baseEffects), weight, Set.copyOf(targetItemTypes));
        }
    }
}
