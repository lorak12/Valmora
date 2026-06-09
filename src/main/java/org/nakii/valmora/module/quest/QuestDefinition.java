package org.nakii.valmora.module.quest;

import java.util.List;

public class QuestDefinition {
    private final String id;
    private final String name;
    private final List<QuestObjective> objectives;

    public QuestDefinition(String id, String name, List<QuestObjective> objectives) {
        this.id = id;
        this.name = name;
        this.objectives = objectives;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<QuestObjective> getObjectives() { return objectives; }
}
