package org.nakii.valmora.module.npc.dialogue;

import org.nakii.valmora.module.script.compile.CompiledConditions;
import org.nakii.valmora.module.script.compile.CompiledScript;

import java.util.List;

public class DialogueNode {
    public enum NodeType { NPC, PLAYER }

    private final String id;
    private final String text;
    private final List<String> events;
    /** Conditions required for this option to be shown (condition DSL strings). */
    private final List<String> conditions;
    private final List<DialogueChoice> choices;
    private final NodeType nodeType;
    private final CompiledScript compiledEvents;
    private final CompiledConditions compiledConditions;

    public DialogueNode(String id, String text, List<String> events, List<DialogueChoice> choices) {
        this(id, text, events, List.of(), choices, NodeType.NPC);
    }

    public DialogueNode(String id, String text, List<String> events,
                        List<String> conditions, List<DialogueChoice> choices) {
        this(id, text, events, conditions, choices, NodeType.NPC);
    }

    public DialogueNode(String id, String text, List<String> events,
                        List<String> conditions, List<DialogueChoice> choices, NodeType nodeType) {
        this.id = id;
        this.text = text;
        this.events = events != null ? events : List.of();
        this.conditions = conditions != null ? conditions : List.of();
        this.choices = choices != null ? choices : List.of();
        this.nodeType = nodeType != null ? nodeType : NodeType.NPC;
        this.compiledEvents = CompiledScript.compile(this.events, "events");
        this.compiledConditions = CompiledConditions.compile(this.conditions, "conditions");
    }

    public CompiledScript getCompiledEvents() { return compiledEvents; }
    public CompiledConditions getCompiledConditions() { return compiledConditions; }

    public String getId() { return id; }
    public String getText() { return text; }
    public List<String> getEvents() { return events; }
    public List<String> getConditions() { return conditions; }
    public List<DialogueChoice> getChoices() { return choices; }
    public NodeType getNodeType() { return nodeType; }
    public boolean isPlayerNode() { return nodeType == NodeType.PLAYER; }
}
