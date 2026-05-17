package org.nakii.valmora.module.npc.dialogue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DialogueDefinition {
    private final String id;
    /** Visual NPC name shown during conversation. */
    private final String questerName;
    /** Ordered list of NPC option IDs tried at the start — first one whose conditions pass is used. */
    private final List<String> firstOptions;
    /** Legacy single start node (used when firstOptions is empty). */
    private final String startNodeId;
    /** If true, the player cannot walk away during the conversation. */
    private final boolean stop;
    /** Actions fired when the conversation ends for any reason. */
    private final List<String> finalActions;
    private final Map<String, DialogueNode> nodes;

    public DialogueDefinition(String id, String startNodeId, Map<String, DialogueNode> nodes) {
        this(id, id, List.of(), startNodeId, false, List.of(), nodes);
    }

    public DialogueDefinition(String id, String questerName, List<String> firstOptions,
                              String startNodeId, boolean stop, List<String> finalActions,
                              Map<String, DialogueNode> nodes) {
        this.id = id;
        this.questerName = questerName;
        this.firstOptions = firstOptions != null ? firstOptions : List.of();
        this.startNodeId = startNodeId;
        this.stop = stop;
        this.finalActions = finalActions != null ? finalActions : List.of();
        this.nodes = nodes;
    }

    public String getId() { return id; }
    public String getQuesterName() { return questerName; }
    public List<String> getFirstOptions() { return firstOptions; }
    public String getStartNodeId() { return startNodeId; }
    public boolean isStop() { return stop; }
    public List<String> getFinalActions() { return finalActions; }
    public Optional<DialogueNode> getNode(String nodeId) { return Optional.ofNullable(nodes.get(nodeId)); }
    public Map<String, DialogueNode> getAllNodes() { return nodes; }
}
