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
    /**
     * Seconds after completion before the quest can be started again (0 = not repeatable, the
     * default — matches every quest defined before this field existed). Previously the only way
     * to get repeat behavior was slayer content faking it with an explicit
     * {@code quest_cancel}+{@code quest_start} script pair (see docs/modules/design/slayer.md).
     */
    private final long cooldownSeconds;

    public QuestDefinition(String id, String name, List<QuestObjective> objectives) {
        this(id, name, objectives, List.of(), 0);
    }

    public QuestDefinition(String id, String name, List<QuestObjective> objectives, List<String> rewardEvents) {
        this(id, name, objectives, rewardEvents, 0);
    }

    public QuestDefinition(String id, String name, List<QuestObjective> objectives, List<String> rewardEvents, long cooldownSeconds) {
        this.id = id;
        this.name = name;
        this.objectives = objectives;
        this.rewardEvents = rewardEvents != null ? rewardEvents : List.of();
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public List<String> getRewardEvents() { return rewardEvents; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public boolean isRepeatable() { return cooldownSeconds > 0; }
}
