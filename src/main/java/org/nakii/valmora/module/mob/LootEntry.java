package org.nakii.valmora.module.mob;

import org.bukkit.inventory.ItemStack;

public class LootEntry {
    private final ItemStack item;
    private final int minAmount;
    private final int maxAmount;
    private final double chance;
    private final boolean luckAffected;

    public LootEntry(ItemStack item, int minAmount, int maxAmount, double chance, boolean luckAffected) {
        this.item = item;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.luckAffected = luckAffected;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public double getChance() {
        return chance;
    }

    public boolean isLuckAffected() {
        return luckAffected;
    }

    public double getEffectiveChance(double luck) {
        if (luckAffected && luck > 0) {
            return chance + (luck / 100.0) * chance;
        }
        return chance;
    }

    public int getRandomAmount() {
        if (minAmount == maxAmount) {
            return minAmount;
        }
        return minAmount + (int) (Math.random() * (maxAmount - minAmount + 1));
    }

    public ItemStack createDroppedItem() {
        ItemStack drop = item.clone();
        drop.setAmount(getRandomAmount());
        return drop;
    }
}
