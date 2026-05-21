package org.nakii.valmora.module.collection;

import java.util.List;

public class CollectionStage {
    private final int number;
    private final long required;
    private final List<String> rewards;

    public CollectionStage(int number, long required, List<String> rewards) {
        this.number = number;
        this.required = required;
        this.rewards = rewards;
    }

    public int getNumber() { return number; }
    public long getRequired() { return required; }
    public List<String> getRewards() { return rewards; }
}
