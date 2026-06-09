package org.nakii.valmora.module.quest;

import java.util.Collections;
import java.util.List;

public class QuestObjective {
    private final String id;
    private final String type;         // lowercase type ID, e.g. "kill", "delay"
    private final String target;
    private final int required;
    private final List<String> conditions;
    private final List<String> events;
    private final boolean persistent;
    private final boolean autoOnce;
    private final int notifyInterval;
    private final long delayTicks;
    private final int intervalTicks;

    public QuestObjective(String id, String type, String target, int required,
                          List<String> conditions, List<String> events,
                          boolean persistent, boolean autoOnce, int notifyInterval,
                          long delayTicks, int intervalTicks) {
        this.id = id;
        this.type = type != null ? type.toLowerCase() : "";
        this.target = target;
        this.required = required;
        this.conditions = conditions != null ? conditions : Collections.emptyList();
        this.events = events != null ? events : Collections.emptyList();
        this.persistent = persistent;
        this.autoOnce = autoOnce;
        this.notifyInterval = notifyInterval;
        this.delayTicks = delayTicks;
        this.intervalTicks = intervalTicks;
    }

    public QuestObjective(String id, String type, String target, int required,
                          List<String> conditions, List<String> events,
                          boolean persistent, boolean autoOnce, int notifyInterval) {
        this(id, type, target, required, conditions, events, persistent, autoOnce, notifyInterval, 0L, 0);
    }

    public QuestObjective(String type, String target, int required) {
        this(null, type, target, required, null, null, false, false, 0, 0L, 0);
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTarget() { return target; }
    public int getRequired() { return required; }
    public List<String> getConditions() { return conditions; }
    public List<String> getEvents() { return events; }
    public boolean isPersistent() { return persistent; }
    public boolean isAutoOnce() { return autoOnce; }
    public int getNotifyInterval() { return notifyInterval; }
    public long getDelayTicks() { return delayTicks; }
    public int getIntervalTicks() { return intervalTicks; }
}
