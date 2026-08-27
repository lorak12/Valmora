package org.nakii.valmora.module.modifier;

import org.nakii.valmora.module.item.ItemType;

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
}
