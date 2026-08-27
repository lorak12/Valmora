package org.nakii.valmora.module.modifier;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the fluent Java builder API (docs/Valmora_Modifier_Framework_Design.docx §20) a plugin
 * uses to register a custom group/modifier without YAML.
 */
class ModifierBuilderTest {

    @Test
    void groupBuilderAppliesDefaults() {
        ModifierGroupDefinition group = ModifierGroupDefinition.builder("my_plugin:corruptions").build();

        assertEquals("my_plugin:corruptions", group.getId());
        assertEquals(DisplayFormat.NONE, group.getDisplayFormat());
        assertEquals(ApplicationMode.MULTIPLE, group.getApplicationMode());
        assertEquals(Integer.MAX_VALUE, group.getMax());
        assertFalse(group.isReplacementAllowed());
        assertTrue(group.isRemovalAllowed());
        assertEquals(StorageMode.STACKED, group.getStorageMode());
        assertEquals(TierSource.INSTANCE, group.getTierSource());
        assertTrue(group.appliesTo(ItemType.SWORD)); // empty target set = applies to everything
    }

    @Test
    void groupBuilderMatchesDesignDocExample() {
        // docs §20: ModifierGroup.builder("my_plugin:corruptions").applicationMode(MULTIPLE).maxApplications(3).build()
        ModifierGroupDefinition group = ModifierGroupDefinition.builder("my_plugin:corruptions")
                .applicationMode(ApplicationMode.MULTIPLE)
                .maxApplications(3)
                .targetItemType(ItemType.SWORD)
                .storageMode(StorageMode.INSTANCES)
                .tierSource(TierSource.RARITY_RANK)
                .build();

        assertEquals(ApplicationMode.MULTIPLE, group.getApplicationMode());
        assertEquals(3, group.getMax());
        assertTrue(group.appliesTo(ItemType.SWORD));
        assertFalse(group.appliesTo(ItemType.BOW));
        assertEquals(StorageMode.INSTANCES, group.getStorageMode());
        assertEquals(TierSource.RARITY_RANK, group.getTierSource());
    }

    @Test
    void modifierBuilderAppliesDefaults() {
        ModifierDefinition def = ModifierDefinition.builder("soul_harvest", "my_plugin:corruptions").build();

        assertEquals("soul_harvest", def.getId());
        assertEquals("my_plugin:corruptions", def.getGroupId());
        assertFalse(def.isTiered());
        assertEquals(1.0, def.getWeight());
        assertTrue(def.getEffects(1).isEmpty());
        assertTrue(def.appliesToItemType(ItemType.SWORD)); // empty restriction = inherits group only
    }

    @Test
    void modifierBuilderCollectsEffectsTagsAndConflicts() {
        ModifierEffect effect = fakeEffect();
        ModifierDefinition def = ModifierDefinition.builder("soul_harvest", "traits")
                .displayName("<dark_purple>Soul Harvest")
                .prefix("<dark_purple>Harvest ")
                .tag("OFFENSIVE")
                .conflictId("fierce")
                .conflictTag("PRECISION_EXCLUSIVE")
                .weight(2.5)
                .targetItemType(ItemType.SWORD)
                .effect(effect)
                .build();

        assertEquals("<dark_purple>Soul Harvest", def.getDisplayName());
        assertEquals("<dark_purple>Harvest ", def.getPrefix());
        assertTrue(def.getTags().contains("OFFENSIVE"));
        assertTrue(def.getConflictIds().contains("fierce"));
        assertTrue(def.getConflictTags().contains("PRECISION_EXCLUSIVE"));
        assertEquals(2.5, def.getWeight());
        assertEquals(List.of(effect), def.getEffects(1));
        assertTrue(def.appliesToItemType(ItemType.SWORD));
        assertFalse(def.appliesToItemType(ItemType.BOW));
    }

    @Test
    void modifierBuilderSupportsTiers() {
        ModifierEffect tier1 = fakeEffect();
        ModifierDefinition def = ModifierDefinition.builder("ruby", "gemstones")
                .tier(1, new ModifierTier(1, "Rough Ruby", List.of(tier1)))
                .build();

        assertTrue(def.isTiered());
        assertEquals(1, def.getMaxTier());
        assertEquals("Rough Ruby", def.getDisplayName(1));
        assertEquals(List.of(tier1), def.getEffects(1));
    }

    private static ModifierEffect fakeEffect() {
        return new ModifierEffect() {
            @Override public String getType() { return "STAT"; }
            @Override public ConditionGroup getConditions() { return new ConditionGroup(Collections.emptyList()); }
        };
    }
}
