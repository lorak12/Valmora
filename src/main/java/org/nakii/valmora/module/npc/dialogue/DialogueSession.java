package org.nakii.valmora.module.npc.dialogue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DialogueSession {
    private final UUID playerUuid;
    private final DialogueDefinition dialogue;
    private String currentNodeId;

    /** The choices currently rendered on screen, in display order. */
    private List<DialogueChoice> displayedChoices = Collections.emptyList();
    /** 0-based index of the currently keyboard-highlighted choice. */
    private int highlightedChoice = 0;
    /** Task ID for the scheduled NPC-to-NPC auto-advance, or -1 if none pending. */
    private int npcAutoAdvanceTaskId = -1;
    /** The NPC node ID we will auto-advance to once the delay expires. */
    private String pendingNpcNodeId = null;

    public DialogueSession(UUID playerUuid, DialogueDefinition dialogue) {
        this.playerUuid = playerUuid;
        this.dialogue = dialogue;
        this.currentNodeId = dialogue.getStartNodeId();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public DialogueDefinition getDialogue() { return dialogue; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String id) { this.currentNodeId = id; }

    public List<DialogueChoice> getDisplayedChoices() { return displayedChoices; }
    public void setDisplayedChoices(List<DialogueChoice> choices) {
        this.displayedChoices = choices;
        this.highlightedChoice = 0;
    }

    public int getHighlightedChoice() { return highlightedChoice; }
    public void setHighlightedChoice(int index) {
        if (displayedChoices.isEmpty()) return;
        this.highlightedChoice = Math.floorMod(index, displayedChoices.size());
    }

    public boolean isAwaitingAutoAdvance() { return npcAutoAdvanceTaskId >= 0; }
    public int getNpcAutoAdvanceTaskId() { return npcAutoAdvanceTaskId; }
    public String getPendingNpcNodeId() { return pendingNpcNodeId; }
    public void setNpcAutoAdvance(int taskId, String nodeId) {
        this.npcAutoAdvanceTaskId = taskId;
        this.pendingNpcNodeId = nodeId;
    }
    public void clearNpcAutoAdvance() {
        this.npcAutoAdvanceTaskId = -1;
        this.pendingNpcNodeId = null;
    }
}
