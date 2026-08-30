package org.nakii.valmora.module.enchant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EtableCostCalculatorTest {

    @Test
    void costScalesLinearlyWithLevel() {
        assertEquals(2, EtableCostCalculator.cost(1));
        assertEquals(6, EtableCostCalculator.cost(3));
        assertEquals(10, EtableCostCalculator.cost(5));
    }

    @Test
    void zeroOrNegativeLevelCostsNothing() {
        assertEquals(0, EtableCostCalculator.cost(0));
        assertEquals(0, EtableCostCalculator.cost(-3));
    }
}
