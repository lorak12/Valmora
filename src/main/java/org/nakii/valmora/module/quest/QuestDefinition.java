package org.nakii.valmora.module.quest;

import java.util.List;

public class QuestDefinition {
    private final String id;
    private final String name;
    private final List<QuestObjective> objectives;
    private final List<String> rewards;
    private final List<String> onStartEvents;

    public QuestDefinition(String id, String name, List<QuestObjective> objectives,
                           List<String> rewards, List<String> onStartEvents) {
        this.id = id; this.name = name; this.objectives = objectives;
        this.rewards = rewards; this.onStartEvents = onStartEvents;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public List<String> getRewards() { return rewards; }
    public List<String> getOnStartEvents() { return onStartEvents; }
}
