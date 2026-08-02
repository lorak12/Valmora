package org.nakii.valmora.module.quest;

import java.util.List;

public class QuestDefinition {
    private final String id;
    private final String name;
    private final List<QuestObjective> objectives;
    /**
     * Optional top-level reward events, distinct from per-objective {@code events}. Not fired
     * automatically by {@link QuestManager} — intended for systems (e.g. a quest board) that
     * grant rewards on an explicit player action (a "Collect" click) rather than the instant
     * the last objective completes.
     */
    private final List<String> rewardEvents;

    public QuestDefinition(String id, String name, List<QuestObjective> objectives) {
        this(id, name, objectives, List.of());
    }

    public QuestDefinition(String id, String name, List<QuestObjective> objectives, List<String> rewardEvents) {
        this.id = id;
        this.name = name;
        this.objectives = objectives;
        this.rewardEvents = rewardEvents != null ? rewardEvents : List.of();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public List<String> getRewardEvents() { return rewardEvents; }
}
