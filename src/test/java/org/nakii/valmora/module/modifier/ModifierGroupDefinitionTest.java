package org.nakii.valmora.module.modifier;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.item.ItemType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModifierGroupDefinitionTest {

    @Test
    void emptyTargetSetAppliesToEverything() {
        ModifierGroupDefinition group = new ModifierGroupDefinition("traits", DisplayFormat.LORE, 0,
                ApplicationMode.MULTIPLE, 3, true, true, Set.of(), StorageMode.STACKED, TierSource.INSTANCE);
        assertTrue(group.appliesTo(ItemType.SWORD));
        assertTrue(group.appliesTo(ItemType.HELMET));
    }

    @Test
    void nonEmptyTargetSetRestrictsApplicability() {
        ModifierGroupDefinition group = new ModifierGroupDefinition("reforges", DisplayFormat.PREFIX, 10,
                ApplicationMode.EXCLUSIVE, 1, true, true, Set.of(ItemType.SWORD, ItemType.AXE), StorageMode.SINGLE, TierSource.RARITY_RANK);
        assertTrue(group.appliesTo(ItemType.SWORD));
        assertTrue(group.appliesTo(ItemType.AXE));
        assertFalse(group.appliesTo(ItemType.BOW));
    }

    @Test
    void exposesRawAttachmentRuleFields() {
        ModifierGroupDefinition group = new ModifierGroupDefinition("gemstones", DisplayFormat.LORE, 40,
                ApplicationMode.STACKABLE, 5, false, true, Set.of(ItemType.SWORD), StorageMode.INSTANCES, TierSource.INSTANCE);
        assertEquals(ApplicationMode.STACKABLE, group.getApplicationMode());
        assertEquals(5, group.getMax());
        assertFalse(group.isReplacementAllowed());
        assertTrue(group.isRemovalAllowed());
        assertEquals(StorageMode.INSTANCES, group.getStorageMode());
        assertEquals(40, group.getDisplayOrder());
    }
}
