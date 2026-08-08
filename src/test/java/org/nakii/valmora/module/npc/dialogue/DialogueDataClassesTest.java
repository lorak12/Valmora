package org.nakii.valmora.module.npc.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the small dialogue data classes: DialogueDefinition, DialogueNode, DialogueChoice, DialogueSession. */
class DialogueDataClassesTest {

    // --- DialogueDefinition ---

    @Test
    void definition_shortConstructor_defaultsQuesterNameToIdAndEmptyLists() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", node));

        assertEquals("greet", def.getId());
        assertEquals("greet", def.getQuesterName());
        assertTrue(def.getFirstOptions().isEmpty());
        assertEquals("start", def.getStartNodeId());
        assertFalse(def.isStop());
        assertTrue(def.getFinalActions().isEmpty());
        assertTrue(def.getNode("start").isPresent());
        assertTrue(def.getNode("missing").isEmpty());
    }

    @Test
    void definition_fullConstructor_nullListsDefaultToEmpty() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "Elder", null, "start", true, null, Map.of("start", node));

        assertTrue(def.getFirstOptions().isEmpty());
        assertTrue(def.getFinalActions().isEmpty());
        assertTrue(def.isStop());
        assertEquals("Elder", def.getQuesterName());
    }

    // --- DialogueNode ---

    @Test
    void node_defaultsToNpcType() {
        DialogueNode node = new DialogueNode("n1", "text", List.of("give STONE:1"), List.of());
        assertEquals(DialogueNode.NodeType.NPC, node.getNodeType());
        assertFalse(node.isPlayerNode());
        assertEquals(List.of("give STONE:1"), node.getEvents());
    }

    @Test
    void node_playerType_isPlayerNodeTrue() {
        DialogueNode node = new DialogueNode("player.p1", "text", List.of(), List.of(), List.of(), DialogueNode.NodeType.PLAYER);
        assertTrue(node.isPlayerNode());
    }

    @Test
    void node_nullListsDefaultToEmpty() {
        DialogueNode node = new DialogueNode("n1", "text", null, null, null, null);
        assertTrue(node.getEvents().isEmpty());
        assertTrue(node.getConditions().isEmpty());
        assertTrue(node.getChoices().isEmpty());
        assertEquals(DialogueNode.NodeType.NPC, node.getNodeType()); // null type defaults to NPC
    }

    // --- DialogueChoice ---

    @Test
    void choice_shortConstructor_emptyConditions() {
        DialogueChoice choice = new DialogueChoice("Continue", "next", List.of("give STONE:1"));
        assertEquals("Continue", choice.getText());
        assertEquals("next", choice.getNextNodeId());
        assertEquals(List.of("give STONE:1"), choice.getEvents());
        assertTrue(choice.getConditions().isEmpty());
    }

    @Test
    void choice_nullListsDefaultToEmpty() {
        DialogueChoice choice = new DialogueChoice("Continue", "next", null, null);
        assertTrue(choice.getEvents().isEmpty());
        assertTrue(choice.getConditions().isEmpty());
    }

    // --- DialogueSession ---

    @Test
    void session_startsAtDialogueStartNode() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", node));
        DialogueSession session = new DialogueSession(UUID.randomUUID(), def);

        assertEquals("start", session.getCurrentNodeId());
        assertTrue(session.getDisplayedChoices().isEmpty());
        assertFalse(session.isAwaitingAutoAdvance());
    }

    @Test
    void session_setDisplayedChoices_resetsHighlightToZero() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", node));
        DialogueSession session = new DialogueSession(UUID.randomUUID(), def);

        List<DialogueChoice> choices = List.of(
                new DialogueChoice("A", "a", List.of()),
                new DialogueChoice("B", "b", List.of()));
        session.setDisplayedChoices(choices);
        session.setHighlightedChoice(1);
        assertEquals(1, session.getHighlightedChoice());

        session.setDisplayedChoices(choices); // re-render resets highlight
        assertEquals(0, session.getHighlightedChoice());
    }

    @Test
    void session_setHighlightedChoice_wrapsWithFloorMod() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", node));
        DialogueSession session = new DialogueSession(UUID.randomUUID(), def);
        session.setDisplayedChoices(List.of(
                new DialogueChoice("A", "a", List.of()),
                new DialogueChoice("B", "b", List.of())));

        session.setHighlightedChoice(-1);
        assertEquals(1, session.getHighlightedChoice()); // floorMod(-1, 2) == 1

        session.setHighlightedChoice(3);
        assertEquals(1, session.getHighlightedChoice()); // floorMod(3, 2) == 1
    }

    @Test
    void session_setHighlightedChoice_noOpWhenNoChoicesDisplayed() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", node));
        DialogueSession session = new DialogueSession(UUID.randomUUID(), def);

        assertDoesNotThrow(() -> session.setHighlightedChoice(5));
        assertEquals(0, session.getHighlightedChoice());
    }

    @Test
    void session_autoAdvanceLifecycle() {
        DialogueNode node = new DialogueNode("start", "Hi", List.of(), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", node));
        DialogueSession session = new DialogueSession(UUID.randomUUID(), def);

        session.setNpcAutoAdvance(42, "next_node");
        assertTrue(session.isAwaitingAutoAdvance());
        assertEquals(42, session.getNpcAutoAdvanceTaskId());
        assertEquals("next_node", session.getPendingNpcNodeId());

        session.clearNpcAutoAdvance();
        assertFalse(session.isAwaitingAutoAdvance());
        assertNull(session.getPendingNpcNodeId());
    }
}
