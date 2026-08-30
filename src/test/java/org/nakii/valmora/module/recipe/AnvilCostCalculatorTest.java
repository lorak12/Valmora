package org.nakii.valmora.module.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the unified anvil's "prior work penalty" math (coworker anvil spec §6). */
public class AnvilCostCalculatorTest {

    @Test
    void penaltyFollowsTheDocumentedCurve() {
        assertEquals(0, AnvilCostCalculator.penalty(0));
        assertEquals(1, AnvilCostCalculator.penalty(1));
        assertEquals(3, AnvilCostCalculator.penalty(2));
        assertEquals(7, AnvilCostCalculator.penalty(3));
        assertEquals(15, AnvilCostCalculator.penalty(4));
    }

    @Test
    void penaltyNeverGoesNegativeForNonPositiveWork() {
        assertEquals(0, AnvilCostCalculator.penalty(-5));
    }

    @Test
    void resultWorkCountIncrementsMaxByDefault() {
        assertEquals(4, AnvilCostCalculator.resultWorkCount(3, 1, true));
        assertEquals(1, AnvilCostCalculator.resultWorkCount(0, 0, true));
    }

    @Test
    void resultWorkCountCarriesOverSlotAWhenPenaltyDisabled() {
        assertEquals(3, AnvilCostCalculator.resultWorkCount(3, 5, false));
    }

    @Test
    void totalXpCostSumsEverything() {
        // base 5 + enchants 10 + penalty(2)=3 + penalty(1)=1 -> 19
        assertEquals(19, AnvilCostCalculator.totalXpCost(5, 10, 2, 1));
    }
}
