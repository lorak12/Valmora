package org.nakii.valmora.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the pure scaling formula extracted from {@code module.resource.ResourceManager} — the
 * player-lookup half ({@code getPlayerMiningFortune}) needs a live {@code ValmoraAPI}/session and
 * is exercised indirectly through {@code ResourceManager}'s existing tests instead.
 */
public class MiningFortuneTest {

    @Test
    public void zeroOrNegativeFortune_returnsBaseAmountUnchanged() {
        assertEquals(5, MiningFortune.applyFortune(5, 0.0));
        assertEquals(5, MiningFortune.applyFortune(5, -10.0));
    }

    @Test
    public void positiveFortune_scalesUpAndNeverBelowBase() {
        // +100% fortune doubles the base amount.
        assertEquals(10, MiningFortune.applyFortune(5, 100.0));
        // A tiny bonus that rounds down to the same base amount still returns at least the base.
        assertEquals(5, MiningFortune.applyFortune(5, 1.0));
    }
}
