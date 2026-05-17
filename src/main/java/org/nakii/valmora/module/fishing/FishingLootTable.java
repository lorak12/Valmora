package org.nakii.valmora.module.fishing;

import java.util.List;

public class FishingLootTable {
    private final String id;
    private final List<FishingLootEntry> entries;
    private final double seaCreatureChance;
    private final String seaCreatureMobId;

    public FishingLootTable(String id, List<FishingLootEntry> entries, double seaCreatureChance, String seaCreatureMobId) {
        this.id = id;
        this.entries = entries;
        this.seaCreatureChance = seaCreatureChance;
        this.seaCreatureMobId = seaCreatureMobId;
    }

    public String getId() { return id; }
    public double getSeaCreatureChance() { return seaCreatureChance; }
    public String getSeaCreatureMobId() { return seaCreatureMobId; }

    public FishingLootEntry roll() {
        if (entries.isEmpty()) return null;
        int totalWeight = entries.stream().mapToInt(FishingLootEntry::getWeight).sum();
        if (totalWeight <= 0) return null;
        int roll = (int)(Math.random() * totalWeight);
        int cumulative = 0;
        for (FishingLootEntry entry : entries) {
            cumulative += entry.getWeight();
            if (roll < cumulative) return entry;
        }
        return entries.get(entries.size() - 1);
    }
}
