package org.nakii.valmora.module.blockloot;

/**
 * One entry in a {@link BlockLootConfig}'s drop table — same shape as
 * {@code module.zone.ZoneResourceDrop}, deliberately duplicated rather than shared: that class
 * belongs to the zone-scoped resource-block system and carries no meaning outside it.
 */
public record BlockLootDrop(String itemId, int minAmount, int maxAmount, double chance) {

    public int rollAmount() {
        if (minAmount >= maxAmount) return minAmount;
        return minAmount + (int) (Math.random() * (maxAmount - minAmount + 1));
    }
}
