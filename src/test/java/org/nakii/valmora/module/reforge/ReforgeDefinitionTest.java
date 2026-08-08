package org.nakii.valmora.module.reforge;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.item.Rarity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Covers {@link ReforgeDefinition}'s pure rarity-fallback/type-matching/weight logic — previously
 *  untested (per docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "unit tests for untested modules"
 *  item). */
public class ReforgeDefinitionTest {

    private ReforgeDefinition definition(Map<Rarity, Map<String, Double>> byRarity, List<ItemType> types, double weight) {
        return new ReforgeDefinition("sharp", "Sharp", types, byRarity, false, weight);
    }

    @Test
    void getStatBonusesForRarityReturnsExactMatchWhenPresent() {
        Map<Rarity, Map<String, Double>> byRarity = new EnumMap<>(Rarity.class);
        byRarity.put(Rarity.COMMON, Map.of("strength", 5.0));
        byRarity.put(Rarity.RARE, Map.of("strength", 15.0));
        ReforgeDefinition def = definition(byRarity, List.of(), 1.0);

        assertEquals(Map.of("strength", 15.0), def.getStatBonusesForRarity(Rarity.RARE));
    }

    @Test
    void getStatBonusesForRarityFallsBackToNearestLowerRarity() {
        Map<Rarity, Map<String, Double>> byRarity = new EnumMap<>(Rarity.class);
        byRarity.put(Rarity.COMMON, Map.of("strength", 5.0));
        // No entry for UNCOMMON or RARE — RARE should fall back to COMMON (nearest lower with data).
        ReforgeDefinition def = definition(byRarity, List.of(), 1.0);

        assertEquals(Map.of("strength", 5.0), def.getStatBonusesForRarity(Rarity.RARE));
    }

    @Test
    void getStatBonusesForRarityReturnsEmptyWhenNoLowerRarityHasData() {
        Map<Rarity, Map<String, Double>> byRarity = new EnumMap<>(Rarity.class);
        byRarity.put(Rarity.LEGENDARY, Map.of("strength", 50.0));
        // COMMON is the lowest rarity — nothing below it to fall back to.
        ReforgeDefinition def = definition(byRarity, List.of(), 1.0);

        assertEquals(Map.of(), def.getStatBonusesForRarity(Rarity.COMMON));
    }

    @Test
    void appliesToReturnsTrueForAnyTypeWhenApplicableTypesEmpty() {
        ReforgeDefinition def = definition(new EnumMap<>(Rarity.class), List.of(), 1.0);
        assertTrue(def.appliesTo(ItemType.SWORD));
        assertTrue(def.appliesTo(ItemType.BOW));
    }

    @Test
    void appliesToRespectsExplicitTypeList() {
        ReforgeDefinition def = definition(new EnumMap<>(Rarity.class), List.of(ItemType.SWORD, ItemType.AXE), 1.0);
        assertTrue(def.appliesTo(ItemType.SWORD));
        assertTrue(def.appliesTo(ItemType.AXE));
        assertFalse(def.appliesTo(ItemType.BOW));
    }

    @Test
    void appliesToHonorsExplicitAllType() {
        ReforgeDefinition def = definition(new EnumMap<>(Rarity.class), List.of(ItemType.ALL), 1.0);
        assertTrue(def.appliesTo(ItemType.BOW));
        assertTrue(def.appliesTo(ItemType.HELMET));
    }

    @Test
    void weightDefaultsToOneWhenNonPositive() {
        ReforgeDefinition zero = definition(new EnumMap<>(Rarity.class), List.of(), 0.0);
        assertEquals(1.0, zero.getWeight());

        ReforgeDefinition negative = definition(new EnumMap<>(Rarity.class), List.of(), -5.0);
        assertEquals(1.0, negative.getWeight());
    }

    @Test
    void weightPreservesPositiveValue() {
        ReforgeDefinition def = definition(new EnumMap<>(Rarity.class), List.of(), 3.5);
        assertEquals(3.5, def.getWeight());
    }

    @Test
    void twoArgConstructorDefaultsWeightToOne() {
        Map<Rarity, Map<String, Double>> byRarity = new EnumMap<>(Rarity.class);
        ReforgeDefinition def = new ReforgeDefinition("id", "Name", List.of(), byRarity, false);
        assertEquals(1.0, def.getWeight());
    }

    @Test
    void gettersExposeIdNameAndGenerateStoneFlag() {
        ReforgeDefinition def = new ReforgeDefinition("sharp", "Sharp", List.of(), new EnumMap<>(Rarity.class), true, 1.0);
        assertEquals("sharp", def.getId());
        assertEquals("Sharp", def.getName());
        assertTrue(def.isGenerateStone());
    }
}
