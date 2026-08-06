package org.nakii.valmora.module.skill;

/**
 * A resolved level<->XP-threshold table for one XP curve (Phase 3.2 — see
 * docs/REFACTOR/PROGRESS.md). Always a flat {@code int[]} lookup table regardless of whether it
 * was authored as an explicit {@code thresholds:} list or a {@code formula:} in
 * {@code skills/xp_curves.yml} — the formula (if any) is evaluated once per level at load time in
 * {@link XpCurveRegistry}, never re-evaluated on the level-up hot path.
 */
public final class XpCurve {

    private final int[] thresholds;

    public XpCurve(int[] thresholds) {
        this.thresholds = thresholds;
    }

    public int getMaxLevel() {
        return thresholds.length;
    }

    public double getXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level > thresholds.length) return thresholds[thresholds.length - 1];
        return thresholds[level - 1];
    }

    public int getLevelFromXp(double xp) {
        for (int i = 0; i < thresholds.length; i++) {
            if (xp < thresholds[i]) return i;
        }
        return thresholds.length;
    }
}
