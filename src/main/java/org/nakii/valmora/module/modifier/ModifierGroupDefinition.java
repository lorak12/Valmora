package org.nakii.valmora.module.modifier;

import org.nakii.valmora.module.item.ItemType;

import java.util.HashSet;
import java.util.Set;

/**
 * Attachment rules for a modifier group (docs/Valmora_Modifier_Framework_Design.docx §6) —
 * exclusivity, stacking, capacity, targets, replacement, and storage semantics. Deliberately generic:
 * no field such as {@code stat_modifier: true} or {@code gemstone: true} that would recreate the
 * hardcoded coupling the framework removes (§6 note, §23 hard constraint).
 */
public class ModifierGroupDefinition {

    private final String id;
    private final DisplayFormat displayFormat;
    private final int displayOrder;
    private final ApplicationMode applicationMode;
    private final int max;
    private final boolean replacement;
    private final boolean removal;
    private final Set<ItemType> targetItemTypes; // empty = no restriction
    private final StorageMode storageMode;
    private final TierSource tierSource;

    public ModifierGroupDefinition(String id, DisplayFormat displayFormat, int displayOrder,
                                    ApplicationMode applicationMode, int max, boolean replacement,
                                    boolean removal, Set<ItemType> targetItemTypes, StorageMode storageMode,
                                    TierSource tierSource) {
        this.id = id;
        this.displayFormat = displayFormat;
        this.displayOrder = displayOrder;
        this.applicationMode = applicationMode;
        this.max = max;
        this.replacement = replacement;
        this.removal = removal;
        this.targetItemTypes = targetItemTypes;
        this.storageMode = storageMode;
        this.tierSource = tierSource;
    }

    public String getId() { return id; }
    public DisplayFormat getDisplayFormat() { return displayFormat; }
    public int getDisplayOrder() { return displayOrder; }
    public ApplicationMode getApplicationMode() { return applicationMode; }
    public int getMax() { return max; }
    public boolean isReplacementAllowed() { return replacement; }
    public boolean isRemovalAllowed() { return removal; }
    public Set<ItemType> getTargetItemTypes() { return targetItemTypes; }
    public StorageMode getStorageMode() { return storageMode; }
    public TierSource getTierSource() { return tierSource; }

    public boolean appliesTo(ItemType type) {
        return targetItemTypes.isEmpty() || targetItemTypes.contains(type);
    }

    /** Fluent Java construction (docs/Valmora_Modifier_Framework_Design.docx §20) for a plugin registering a custom group without YAML. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private DisplayFormat displayFormat = DisplayFormat.NONE;
        private int displayOrder = 0;
        private ApplicationMode applicationMode = ApplicationMode.MULTIPLE;
        private int max = Integer.MAX_VALUE;
        private boolean replacement = false;
        private boolean removal = true;
        private final Set<ItemType> targetItemTypes = new HashSet<>();
        private StorageMode storageMode = StorageMode.STACKED;
        private TierSource tierSource = TierSource.INSTANCE;

        private Builder(String id) { this.id = id; }

        public Builder displayFormat(DisplayFormat format) { this.displayFormat = format; return this; }
        public Builder displayOrder(int order) { this.displayOrder = order; return this; }
        public Builder applicationMode(ApplicationMode mode) { this.applicationMode = mode; return this; }
        public Builder maxApplications(int max) { this.max = max; return this; }
        public Builder replacement(boolean replacement) { this.replacement = replacement; return this; }
        public Builder removal(boolean removal) { this.removal = removal; return this; }
        public Builder targetItemType(ItemType type) { this.targetItemTypes.add(type); return this; }
        public Builder targetItemTypes(Set<ItemType> types) { this.targetItemTypes.addAll(types); return this; }
        public Builder storageMode(StorageMode mode) { this.storageMode = mode; return this; }
        public Builder tierSource(TierSource source) { this.tierSource = source; return this; }

        public ModifierGroupDefinition build() {
            return new ModifierGroupDefinition(id, displayFormat, displayOrder, applicationMode, max,
                    replacement, removal, Set.copyOf(targetItemTypes), storageMode, tierSource);
        }
    }
}
