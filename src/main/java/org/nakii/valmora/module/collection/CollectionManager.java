package org.nakii.valmora.module.collection;

import java.util.HashMap;
import java.util.Map;

public class CollectionManager {
    private final Map<String, Long> counts = new HashMap<>();

    public long getCount(String collectionId) {
        return counts.getOrDefault(collectionId.toLowerCase(), 0L);
    }

    public void addCount(String collectionId, long amount) {
        counts.merge(collectionId.toLowerCase(), amount, Long::sum);
    }

    public int getCurrentStage(String collectionId, CollectionDefinition def) {
        if (def == null) return 0;
        return def.getStageForCount(getCount(collectionId));
    }

    public void loadData(Map<String, Long> data) {
        counts.clear();
        if (data != null) {
            data.forEach((k, v) -> counts.put(k.toLowerCase(), v));
        }
    }

    public Map<String, Long> getSaveData() {
        return new HashMap<>(counts);
    }
}
