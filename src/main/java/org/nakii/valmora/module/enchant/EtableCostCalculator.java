package org.nakii.valmora.module.enchant;

/**
 * Pure math for the enchanting table's XP-level cost — mirrors
 * {@link org.nakii.valmora.module.recipe.AnvilCostCalculator} in spirit (a small, pure, unit-tested
 * cost function kept separate from the GUI/event wiring that charges it) but not its shape: there's
 * no "prior work" concept for a fresh table application, just a flat per-level cost that scales
 * with the level being requested — the higher the level, the more it costs, matching the intuition
 * behind vanilla's own enchanting-table cost curve.
 */
public final class EtableCostCalculator {

    private EtableCostCalculator() {
    }

    /** XP levels required to apply {@code level} levels of an enchant at the table. */
    public static int cost(int level) {
        if (level <= 0) return 0;
        return level * 2;
    }
}
