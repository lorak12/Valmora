package org.nakii.valmora.module.blockloot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockLootDropTest {

    @Test
    public void rollAmount_minEqualsMax_isDeterministic() {
        BlockLootDrop drop = new BlockLootDrop("iron_dust", 3, 3, 1.0);
        for (int i = 0; i < 20; i++) {
            assertEquals(3, drop.rollAmount());
        }
    }

    @Test
    public void rollAmount_minGreaterThanMax_treatedAsMin() {
        // Same defensive convention as ZoneResourceDrop.rollAmount(): minAmount >= maxAmount short-circuits.
        BlockLootDrop drop = new BlockLootDrop("iron_dust", 5, 2, 1.0);
        assertEquals(5, drop.rollAmount());
    }

    @Test
    public void rollAmount_range_alwaysWithinBounds() {
        BlockLootDrop drop = new BlockLootDrop("iron_dust", 1, 4, 1.0);
        for (int i = 0; i < 200; i++) {
            int amount = drop.rollAmount();
            assertTrue(amount >= 1 && amount <= 4, "rolled amount out of bounds: " + amount);
        }
    }
}
