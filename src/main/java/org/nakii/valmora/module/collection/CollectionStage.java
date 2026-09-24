package org.nakii.valmora.module.collection;

import java.util.List;

public class CollectionStage {
    private final int number;
    private final long required;
    private final List<String> rewards;
    private final String id;

    public CollectionStage(int number, long required, List<String> rewards) {
        this(number, required, rewards, null);
    }

    public CollectionStage(int number, long required, List<String> rewards, String id) {
        this.number = number;
        this.required = required;
        this.rewards = rewards;
        this.id = id;
    }

    public int getNumber() { return number; }

    /**
     * Stable identity for the reward ledger: the stage's explicit {@code id}, else its
     * {@code required} threshold. Unlike the stage number, it doesn't change when stages are
     * inserted or renumbered, so a stage's rewards are granted exactly once per player.
     */
    public String getKey() { return id != null ? id : "req:" + required; }
    public long getRequired() { return required; }
    public List<String> getRewards() { return rewards; }
}
