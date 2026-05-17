package org.nakii.valmora.module.fishing;

public class FishingLootEntry {
    private final String itemId;
    private final int weight;
    private final int minAmount;
    private final int maxAmount;

    public FishingLootEntry(String itemId, int weight, int minAmount, int maxAmount) {
        this.itemId = itemId;
        this.weight = weight;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public String getItemId() { return itemId; }
    public int getWeight() { return weight; }
    public int rollAmount() {
        if (minAmount >= maxAmount) return minAmount;
        return minAmount + (int)(Math.random() * (maxAmount - minAmount + 1));
    }
}
