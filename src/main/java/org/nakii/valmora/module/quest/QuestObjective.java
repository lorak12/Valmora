package org.nakii.valmora.module.quest;

import java.util.Collections;
import java.util.List;

public class QuestObjective {
    private final String id;
    private final QuestObjectiveType type;
    private final String target;
    private final int required;
    private final List<String> conditions;
    private final List<String> events;
    private final boolean persistent;
    private final boolean autoOnce;
    private final int notifyInterval;

    public QuestObjective(String id, QuestObjectiveType type, String target, int required,
                          List<String> conditions, List<String> events,
                          boolean persistent, boolean autoOnce, int notifyInterval) {
        this.id = id;
        this.type = type;
        this.target = target;
        this.required = required;
        this.conditions = conditions != null ? conditions : Collections.emptyList();
        this.events = events != null ? events : Collections.emptyList();
        this.persistent = persistent;
        this.autoOnce = autoOnce;
        this.notifyInterval = notifyInterval;
    }

    /** Backward-compatible constructor for existing code that doesn't set the new fields. */
    public QuestObjective(QuestObjectiveType type, String target, int required) {
        this(null, type, target, required, null, null, false, false, 0);
    }

    public String getId() { return id; }
    public QuestObjectiveType getType() { return type; }
    public String getTarget() { return target; }
    public int getRequired() { return required; }
    public List<String> getConditions() { return conditions; }
    public List<String> getEvents() { return events; }
    public boolean isPersistent() { return persistent; }
    public boolean isAutoOnce() { return autoOnce; }
    public int getNotifyInterval() { return notifyInterval; }
}
