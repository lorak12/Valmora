package org.nakii.valmora.module.modifier;

/**
 * How a modifier instance's effective tier is chosen for a group (generic — applies uniformly to
 * every modifier in the group, never special-cased per modifier id; see CLAUDE.md §23-equivalent
 * hard constraint in docs/Valmora_Modifier_Framework_Design.docx).
 */
public enum TierSource {
    /** The tier stored on the {@code ModifierInstance} at application time is used as-is (e.g. gemstones — the tier is chosen by which gem item was used). */
    INSTANCE,
    /**
     * The tier is derived from the carrying item's rarity rank ({@code rank + 1}, clamped to the
     * modifier's declared tier range) every time effects are resolved, ignoring the instance's
     * stored tier. This is how the migrated {@code reforges} group reproduces the legacy
     * per-rarity stat tables losslessly as {@code tiers: 1..7} instead of a banned
     * {@code stat-bonuses-by-rarity} map — see docs/MODIFIER_FRAMEWORK_BACKLOG.md.
     */
    RARITY_RANK
}
