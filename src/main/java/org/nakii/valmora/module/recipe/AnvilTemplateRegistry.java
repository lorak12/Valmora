package org.nakii.valmora.module.recipe;

import org.nakii.valmora.Valmora;

/**
 * Loads the {@code anvil:} section of {@code config.yml} — the unified anvil's tunable magic
 * numbers (coworker anvil spec §2/§6): merge cost-per-enchant-level (Phase 4.3 of the original
 * refactor), a flat per-operation base XP cost, the durability-merge bonus percentage, and the
 * repair-per-unit percentage. Missing keys keep the documented defaults, so an admin who never
 * touches this section sees sensible out-of-the-box behavior.
 *
 * <p>Previously a standalone {@code recipes/anvil_templates.yml} — folded into {@code config.yml}
 * so the recipes folder holds only actual recipes (see CLAUDE.md §9.3).
 */
public class AnvilTemplateRegistry {

    // Was "10 coins per level" pre-unification; the unified anvil charges XP levels throughout
    // (coworker anvil spec §6 frames the whole cost model as XP levels, not coins), so the default
    // is lowered to something sane in that currency rather than carrying the old coin-scale number
    // forward unchanged. Server admins can retune via config.yml's anvil.templates section.
    private static final int DEFAULT_COST_PER_LEVEL = 2;
    private static final int DEFAULT_MERGE_BASE_COST = 0;
    private static final int DEFAULT_REPAIR_BASE_COST = 0;
    private static final double DEFAULT_DURABILITY_BONUS_PERCENT = 0.12;
    private static final double DEFAULT_REPAIR_PERCENT_PER_UNIT = 0.25;

    private int mergeCostPerLevel = DEFAULT_COST_PER_LEVEL;
    private int mergeBaseCost = DEFAULT_MERGE_BASE_COST;
    private int repairBaseCost = DEFAULT_REPAIR_BASE_COST;
    private double durabilityBonusPercent = DEFAULT_DURABILITY_BONUS_PERCENT;
    private double repairPercentPerUnit = DEFAULT_REPAIR_PERCENT_PER_UNIT;

    public void load(Valmora plugin) {
        mergeCostPerLevel = DEFAULT_COST_PER_LEVEL;
        mergeBaseCost = DEFAULT_MERGE_BASE_COST;
        repairBaseCost = DEFAULT_REPAIR_BASE_COST;
        durabilityBonusPercent = DEFAULT_DURABILITY_BONUS_PERCENT;
        repairPercentPerUnit = DEFAULT_REPAIR_PERCENT_PER_UNIT;

        mergeCostPerLevel = plugin.getConfig().getInt("anvil.templates.merge.cost-per-level", DEFAULT_COST_PER_LEVEL);
        mergeBaseCost = plugin.getConfig().getInt("anvil.templates.merge.base-cost", DEFAULT_MERGE_BASE_COST);
        repairBaseCost = plugin.getConfig().getInt("anvil.templates.repair.base-cost", DEFAULT_REPAIR_BASE_COST);
        durabilityBonusPercent = plugin.getConfig().getDouble("anvil.templates.merge.durability-bonus-percent", DEFAULT_DURABILITY_BONUS_PERCENT);
        repairPercentPerUnit = plugin.getConfig().getDouble("anvil.templates.repair.percent-per-unit", DEFAULT_REPAIR_PERCENT_PER_UNIT);
        plugin.getLogger().info("[AnvilTemplateRegistry] merge cost-per-level = " + mergeCostPerLevel);
    }

    public int getMergeCostPerLevel() { return mergeCostPerLevel; }
    public int getMergeBaseCost() { return mergeBaseCost; }
    public int getRepairBaseCost() { return repairBaseCost; }
    public double getDurabilityBonusPercent() { return durabilityBonusPercent; }
    public double getRepairPercentPerUnit() { return repairPercentPerUnit; }

    public void clear() {
        mergeCostPerLevel = DEFAULT_COST_PER_LEVEL;
        mergeBaseCost = DEFAULT_MERGE_BASE_COST;
        repairBaseCost = DEFAULT_REPAIR_BASE_COST;
        durabilityBonusPercent = DEFAULT_DURABILITY_BONUS_PERCENT;
        repairPercentPerUnit = DEFAULT_REPAIR_PERCENT_PER_UNIT;
    }
}
