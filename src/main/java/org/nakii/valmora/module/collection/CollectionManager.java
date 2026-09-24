package org.nakii.valmora.module.collection;

import java.util.HashMap;
import java.util.Map;

public class CollectionManager {
    private final Map<String, Long> counts = new HashMap<>();
    /** Highest stage number already granted its rewards, per collection id — the reward ledger (added 2026-08-07). */
    private final Map<String, Integer> grantedStages = new HashMap<>();
    /**
     * Collection id → keys ({@link CollectionStage#getKey()}) of the stages whose rewards were
     * granted. Replaces the stage-number floor above as the source of truth (the floor is still
     * maintained for display and compatibility). A number floor broke on renumbering: stages
     * inserted below it were never granted, and shifting stages up re-granted them.
     */
    private final Map<String, java.util.Set<String>> grantedKeys = new HashMap<>();

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

    /** Highest stage whose rewards have already fired for this collection — 0 if none yet. */
    public int getGrantedStage(String collectionId) {
        return grantedStages.getOrDefault(collectionId.toLowerCase(), 0);
    }

    /** Records that rewards through (and including) {@code stage} have been granted, so they never re-fire — even across config edits or reloads. */
    public void setGrantedStage(String collectionId, int stage) {
        grantedStages.put(collectionId.toLowerCase(), stage);
    }

    /**
     * Persisted shape for the {@code collections} DB column. Was a bare {@code Map<String, Long>}
     * (counts only) — extended (2026-08-07) to also carry the reward-grant ledger, so stage
     * rewards fire exactly once even across config edits/reloads instead of every time the
     * derived stage happens to be recomputed above the last-seen value.
     */
    /**
     * Keys of the stages already rewarded for {@code def}. The first time a collection is seen
     * without key-ledger data (saves from before it existed), it's seeded from the old number
     * floor using the current definition.
     */
    public java.util.Set<String> getGrantedKeys(CollectionDefinition def) {
        String id = def.getId().toLowerCase();
        java.util.Set<String> keys = grantedKeys.get(id);
        if (keys == null) {
            keys = new java.util.HashSet<>();
            int floor = grantedStages.getOrDefault(id, 0);
            for (CollectionStage stage : def.getStages()) {
                if (stage.getNumber() <= floor) keys.add(stage.getKey());
            }
            grantedKeys.put(id, keys);
        }
        return keys;
    }

    /** Clears a collection's reward ledger (admin reset). */
    public void resetGranted(String collectionId) {
        grantedStages.put(collectionId.toLowerCase(), 0);
        grantedKeys.put(collectionId.toLowerCase(), new java.util.HashSet<>());
    }

    public static class SaveData {
        public Map<String, Long> counts = new HashMap<>();
        public Map<String, Integer> grantedStages = new HashMap<>();
        /** Stage-key ledger (see grantedKeys); absent in saves from before it existed. */
        public Map<String, java.util.Set<String>> grantedKeys = new HashMap<>();
    }

    public SaveData getSaveData() {
        SaveData data = new SaveData();
        data.counts = new HashMap<>(counts);
        data.grantedStages = new HashMap<>(grantedStages);
        grantedKeys.forEach((k, v) -> data.grantedKeys.put(k, new java.util.HashSet<>(v)));
        return data;
    }

    public void loadData(SaveData data) {
        counts.clear();
        grantedStages.clear();
        grantedKeys.clear();
        if (data == null) return;
        if (data.counts != null) data.counts.forEach((k, v) -> counts.put(k.toLowerCase(), v));
        if (data.grantedStages != null) data.grantedStages.forEach((k, v) -> grantedStages.put(k.toLowerCase(), v));
        if (data.grantedKeys != null) data.grantedKeys.forEach((k, v) -> grantedKeys.put(k.toLowerCase(), new java.util.HashSet<>(v)));
    }

    /**
     * Back-compat for the pre-extension save format (a bare counts map, no ledger). A profile
     * loaded this way starts with every collection's granted-stage floor at 0 — the next matching
     * gameplay event will re-fire rewards for every stage the player's count has already passed,
     * a one-time "catch-up" re-grant on first load after the upgrade.
     */
    public void loadData(Map<String, Long> data) {
        counts.clear();
        grantedStages.clear();
        grantedKeys.clear();
        if (data != null) {
            data.forEach((k, v) -> counts.put(k.toLowerCase(), v));
        }
    }
}
