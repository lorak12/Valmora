package org.nakii.valmora.module.npc.dialogue;

import java.util.List;

public class DialogueChoice {
    private final String text;
    private final String nextNodeId;
    private final List<String> events;
    /** Conditions required for this choice to be shown (condition DSL strings). */
    private final List<String> conditions;

    public DialogueChoice(String text, String nextNodeId, List<String> events) {
        this(text, nextNodeId, events, List.of());
    }

    public DialogueChoice(String text, String nextNodeId, List<String> events, List<String> conditions) {
        this.text = text;
        this.nextNodeId = nextNodeId;
        this.events = events != null ? events : List.of();
        this.conditions = conditions != null ? conditions : List.of();
    }

    public String getText() { return text; }
    public String getNextNodeId() { return nextNodeId; }
    public List<String> getEvents() { return events; }
    public List<String> getConditions() { return conditions; }
}
