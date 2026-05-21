package org.nakii.valmora.module.fishing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class FishingLootTableTest {

    @Test
    void testRoll_emptyTable_returnsNull() {
        FishingLootTable table = new FishingLootTable("test", List.of(), 0, null);
        assertNull(table.roll());
    }

    @Test
    void testRoll_singleEntry_alwaysReturnsSameEntry() {
        FishingLootEntry fish = new FishingLootEntry("cod", 100, 1, 1);
        FishingLootTable table = new FishingLootTable("test", List.of(fish), 0, null);
        for (int i = 0; i < 20; i++) {
            assertSame(fish, table.roll());
        }
    }

    @Test
    void testRoll_zeroTotalWeight_returnsNull() {
        FishingLootEntry entry = new FishingLootEntry("cod", 0, 1, 1);
        FishingLootTable table = new FishingLootTable("test", List.of(entry), 0, null);
        assertNull(table.roll());
    }

    @Test
    void testRoll_twoEqualWeight_roughlyFiftyFifty() {
        FishingLootEntry common = new FishingLootEntry("cod", 50, 1, 1);
        FishingLootEntry rare = new FishingLootEntry("salmon", 50, 1, 1);
        FishingLootTable table = new FishingLootTable("test", List.of(common, rare), 0, null);

        int commonCount = 0;
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            if (table.roll() == common) commonCount++;
        }
        // Expect roughly 50% ± 5%
        double ratio = (double) commonCount / total;
        assertTrue(ratio >= 0.45 && ratio <= 0.55,
                "Expected ~50% distribution, got " + ratio);
    }

    @Test
    void testRollAmount_fixedAmount_returnsFixed() {
        FishingLootEntry entry = new FishingLootEntry("cod", 1, 3, 3);
        for (int i = 0; i < 50; i++) {
            assertEquals(3, entry.rollAmount());
        }
    }

    @Test
    void testRollAmount_range_inBounds() {
        FishingLootEntry entry = new FishingLootEntry("cod", 1, 1, 5);
        for (int i = 0; i < 500; i++) {
            int amount = entry.rollAmount();
            assertTrue(amount >= 1 && amount <= 5, "Amount out of range: " + amount);
        }
    }

    @Test
    void testTableGetters_returnConfiguredValues() {
        FishingLootTable table = new FishingLootTable("hub_fishing", List.of(), 0.05, "sea_guardian");
        assertEquals("hub_fishing", table.getId());
        assertEquals(0.05, table.getSeaCreatureChance(), 1e-9);
        assertEquals("sea_guardian", table.getSeaCreatureMobId());
    }
}
