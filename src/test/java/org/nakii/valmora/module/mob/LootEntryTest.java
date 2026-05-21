package org.nakii.valmora.module.mob;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("unit")
class LootEntryTest {

    private ItemStack dummyItem() {
        return mock(ItemStack.class);
    }

    @Test
    void testGetEffectiveChance_noLuck_returnsBaseChance() {
        LootEntry entry = new LootEntry(dummyItem(), 1, 1, 0.10, true);
        assertEquals(0.10, entry.getEffectiveChance(0), 1e-9);
    }

    @Test
    void testGetEffectiveChance_withLuck_increasesChance() {
        LootEntry entry = new LootEntry(dummyItem(), 1, 1, 0.10, true);
        // 0.10 + (100/100.0)*0.10 = 0.20
        assertEquals(0.20, entry.getEffectiveChance(100), 1e-9);
    }

    @Test
    void testGetEffectiveChance_notLuckAffected_ignoresLuck() {
        LootEntry entry = new LootEntry(dummyItem(), 1, 1, 0.10, false);
        assertEquals(0.10, entry.getEffectiveChance(100), 1e-9);
    }

    @Test
    void testGetEffectiveChance_zeroLuck_returnsBaseChance() {
        LootEntry entry = new LootEntry(dummyItem(), 1, 1, 0.50, true);
        assertEquals(0.50, entry.getEffectiveChance(0), 1e-9);
    }

    @Test
    void testGetRandomAmount_sameMinMax_returnsFixed() {
        LootEntry entry = new LootEntry(dummyItem(), 3, 3, 1.0, false);
        for (int i = 0; i < 50; i++) {
            assertEquals(3, entry.getRandomAmount());
        }
    }

    @Test
    void testGetRandomAmount_range_inBounds() {
        LootEntry entry = new LootEntry(dummyItem(), 1, 5, 1.0, false);
        for (int i = 0; i < 500; i++) {
            int amount = entry.getRandomAmount();
            assertTrue(amount >= 1 && amount <= 5, "Amount out of range: " + amount);
        }
    }

    @Test
    void testLootTable_getLuckAffectedEntries_filtersCorrectly() {
        LootEntry luck1 = new LootEntry(dummyItem(), 1, 1, 0.5, true);
        LootEntry noLuck = new LootEntry(dummyItem(), 1, 1, 0.5, false);
        LootEntry luck2 = new LootEntry(dummyItem(), 1, 1, 0.1, true);

        LootTable table = new LootTable(List.of(luck1, noLuck, luck2));
        List<LootEntry> affected = table.getLuckAffectedEntries();

        assertEquals(2, affected.size());
        assertTrue(affected.contains(luck1));
        assertTrue(affected.contains(luck2));
        assertFalse(affected.contains(noLuck));
    }

    @Test
    void testLootTable_empty_getLuckAffected_returnsEmpty() {
        LootTable table = LootTable.empty();
        assertTrue(table.getLuckAffectedEntries().isEmpty());
    }
}
