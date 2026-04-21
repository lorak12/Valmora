package org.nakii.valmora.module.mob;

import java.util.ArrayList;
import java.util.List;

public class LootTable {
    private final List<LootEntry> entries;

    public LootTable(List<LootEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    public List<LootEntry> getLuckAffectedEntries() {
        List<LootEntry> luckEntries = new ArrayList<>();
        for (LootEntry entry : entries) {
            if (entry.isLuckAffected()) {
                luckEntries.add(entry);
            }
        }
        return luckEntries;
    }

    public static LootTable empty() {
        return new LootTable(new ArrayList<>());
    }
}
