package org.nakii.valmora.module.zone;

public class ZoneResourceDrop {
    private final String itemId;
    private final int minAmount;
    private final int maxAmount;
    private final double chance;

    public ZoneResourceDrop(String itemId, int minAmount, int maxAmount, double chance) {
        this.itemId = itemId;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
    }

    public String getItemId() { return itemId; }
    public double getChance() { return chance; }
    public int rollAmount() {
        if (minAmount >= maxAmount) return minAmount;
        return minAmount + (int) (Math.random() * (maxAmount - minAmount + 1));
    }
}
