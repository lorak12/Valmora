package org.nakii.valmora.module.recipe;

/**
 * Pure math for the unified anvil's "prior work penalty" (coworker anvil spec §6):
 * {@code penalty = 2^work_count - 1} (0 uses = +0, 1 use = +1, 2 uses = +3, 3 uses = +7, ...),
 * charged on top of the recipe/enchant-merge base cost for BOTH input items, with the result's
 * own work count inherited as {@code max(workA, workB) + 1} unless the recipe/step opts out via
 * {@code increase_work_penalty: false}.
 *
 * <p>Deliberately not wired into the modifier-recipe step of the unified anvil (gemstone/reforge
 * application already has its own independent {@code ValueResolver}-based cost — see
 * {@code ModifierAnvilHandler}) — only the explicit {@code AnvilRecipeDefinition} (UPGRADE/
 * TRANSMUTE) and standard-combination-engine steps use this.
 */
public final class AnvilCostCalculator {

    private AnvilCostCalculator() {}

    /** {@code 2^workCount - 1}, clamped so an absurd work count can't overflow/go negative. */
    public static int penalty(int workCount) {
        if (workCount <= 0) return 0;
        return (1 << Math.min(workCount, 30)) - 1;
    }

    /** The work count stamped on the result item. When {@code increaseWorkPenalty} is false the
     *  count carries over from slot A (the retained/base item) unchanged, per the note's §6 table. */
    public static int resultWorkCount(int workA, int workB, boolean increaseWorkPenalty) {
        return increaseWorkPenalty ? Math.max(workA, workB) + 1 : workA;
    }

    /** {@code baseCost + enchantCost + penalty(workA) + penalty(workB)}. */
    public static int totalXpCost(int baseCost, int enchantCost, int workA, int workB) {
        return baseCost + enchantCost + penalty(workA) + penalty(workB);
    }
}
