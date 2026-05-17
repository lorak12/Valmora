package org.nakii.valmora.module.quest.hider;

import java.util.List;

public class PlayerHiderEntry {

    private final String id;
    private final List<String> sourceConditions;
    private final List<String> targetConditions;

    public PlayerHiderEntry(String id, List<String> sourceConditions, List<String> targetConditions) {
        this.id = id;
        this.sourceConditions = sourceConditions;
        this.targetConditions = targetConditions;
    }

    public String getId() { return id; }
    public List<String> getSourceConditions() { return sourceConditions; }
    public List<String> getTargetConditions() { return targetConditions; }
}
