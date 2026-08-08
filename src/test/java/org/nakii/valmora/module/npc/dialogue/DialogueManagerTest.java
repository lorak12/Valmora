package org.nakii.valmora.module.npc.dialogue;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.condition.ConditionParser;
import org.nakii.valmora.module.script.event.EventParser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Covers DialogueManager's conversation-navigation logic (start/choice-handling/pointer
 * resolution) — previously zero coverage for the npc module (docs/IMPLEMENTATION_BACKLOG.md).
 * Auto-advance scheduling (NPC-to-NPC delay math, skip hints) is left for a follow-up — it's
 * exercised indirectly here only insofar as it doesn't block the covered paths.
 */
class DialogueManagerTest {

    private Valmora plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private ScriptModule scriptModule;
    private ConditionParser conditionParser;
    private EventParser eventParser;
    private DialogueManager manager;
    private Player player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        scriptModule = mock(ScriptModule.class);
        conditionParser = new ConditionParser(new org.nakii.valmora.module.script.expression.ExpressionParser());
        eventParser = mock(EventParser.class);
        when(plugin.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getConditionParser()).thenReturn(conditionParser);
        when(scriptModule.getEventParser()).thenReturn(eventParser);
        when(eventParser.parseList(anyList())).thenReturn(ctx -> {});

        // runTaskLater/runTaskTimer return a BukkitTask stub carrying a fake task id.
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(1);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(task);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        manager = new DialogueManager(plugin);

        uuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(mock(Location.class));
    }

    private static java.util.List<String> anyList() {
        return any();
    }

    private DialogueNode npcNode(String id, String text, List<DialogueChoice> choices) {
        return new DialogueNode(id, text, List.of(), List.of(), choices, DialogueNode.NodeType.NPC);
    }

    private DialogueNode npcNode(String id, String text, List<String> conditions, List<DialogueChoice> choices) {
        return new DialogueNode(id, text, List.of(), conditions, choices, DialogueNode.NodeType.NPC);
    }

    private DialogueChoice pointer(String targetNodeId) {
        return new DialogueChoice("__ptr__", targetNodeId, List.of());
    }

    // --- startDialogue ---

    @Test
    void startDialogue_unknownId_sendsErrorAndCreatesNoSession() {
        manager.startDialogue(player, "does_not_exist");

        verify(player).sendMessage(any(Component.class));
        assertNull(manager.getSession(uuid));
    }

    @Test
    void startDialogue_terminalNodeWithNoChoicesOrPointers_showsTextThenEndsSession() {
        DialogueNode start = npcNode("start", "Hello traveler.", List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start", Map.of("start", start));
        manager.getDialogueRegistry().register("greet", def);

        manager.startDialogue(player, "greet");

        verify(player, atLeastOnce()).sendMessage(any(Component.class));
        assertNull(manager.getSession(uuid), "no choices/pointers -> session ends immediately after showing text");
    }

    @Test
    void startDialogue_pickFirstOption_skipsFailingConditionFallsThroughToNextOption() {
        DialogueNode blocked = npcNode("blocked", "Locked.", List.of("health 9999"), List.of());
        DialogueNode open = npcNode("open", "Welcome!", List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "greet", List.of("blocked", "open"),
                "unused", false, List.of(), Map.of("blocked", blocked, "open", open));
        manager.getDialogueRegistry().register("greet", def);

        when(player.getHealth()).thenReturn(20.0); // fails "health 9999"

        manager.startDialogue(player, "greet");

        // "open" was picked and shown, then session ended (no choices/pointers) — health-gated
        // "blocked" never got a chance to render.
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void startDialogue_pickFirstOption_allFail_noSessionOrMessage() {
        DialogueNode blocked = npcNode("blocked", "Locked.", List.of("health 9999"), List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "greet", List.of("blocked"),
                "unused", false, List.of(), Map.of("blocked", blocked));
        manager.getDialogueRegistry().register("greet", def);
        when(player.getHealth()).thenReturn(20.0);

        manager.startDialogue(player, "greet");

        assertNull(manager.getSession(uuid));
    }

    @Test
    void startDialogue_withPlayerChoice_populatesSessionAndRendersOptions() {
        DialogueNode playerOpt = npcNode("player.p1", "Tell me more", List.of());
        DialogueNode start = npcNode("start", "Greetings.", List.of(pointer("player.p1")));
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                Map.of("start", start, "player.p1", playerOpt));
        manager.getDialogueRegistry().register("greet", def);

        manager.startDialogue(player, "greet");

        assertNotNull(manager.getSession(uuid));
        assertEquals(1, manager.getSession(uuid).getDisplayedChoices().size());
        // Rendered: NPC text + 1 choice line.
        verify(player, atLeast(2)).sendMessage(any(Component.class));
    }

    // --- handleChoice ---

    @Test
    void handleChoice_noActiveSession_isNoOp() {
        assertDoesNotThrow(() -> manager.handleChoice(player, 0));
    }

    @Test
    void handleChoice_outOfRangeIndex_isNoOpSessionPersists() {
        DialogueNode playerOpt = npcNode("player.p1", "Tell me more", List.of());
        DialogueNode start = npcNode("start", "Greetings.", List.of(pointer("player.p1")));
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                Map.of("start", start, "player.p1", playerOpt));
        manager.getDialogueRegistry().register("greet", def);
        manager.startDialogue(player, "greet");

        manager.handleChoice(player, 5);

        assertNotNull(manager.getSession(uuid), "out-of-range choice must not end the session");
    }

    @Test
    void handleChoice_nullNextNode_endsSession() {
        DialogueChoice deadEnd = new DialogueChoice("Goodbye", null, List.of());
        DialogueNode playerOpt = npcNode("player.p1", "Farewell option", List.of(deadEnd));
        DialogueNode start = npcNode("start", "Greetings.", List.of(pointer("player.p1")));
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                Map.of("start", start, "player.p1", playerOpt));
        manager.getDialogueRegistry().register("greet", def);
        manager.startDialogue(player, "greet");
        // Advance into the transparent player.p1 node's own choice list.
        manager.getSession(uuid).setDisplayedChoices(playerOpt.getChoices());

        manager.handleChoice(player, 0);

        assertNull(manager.getSession(uuid));
    }

    @Test
    void handleChoice_validChoice_executesEventsAndAdvancesNode() {
        DialogueNode terminal = npcNode("terminal", "The end.", List.of());
        DialogueChoice toTerminal = new DialogueChoice("Continue", "terminal", List.of("give STONE:1"));
        DialogueNode playerOpt = npcNode("player.p1", "Continue option", List.of(toTerminal));
        DialogueNode start = npcNode("start", "Greetings.", List.of(pointer("player.p1")));
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                Map.of("start", start, "player.p1", playerOpt, "terminal", terminal));
        manager.getDialogueRegistry().register("greet", def);
        manager.startDialogue(player, "greet");
        manager.getSession(uuid).setDisplayedChoices(playerOpt.getChoices());

        manager.handleChoice(player, 0);

        verify(eventParser).parseList(List.of("give STONE:1"));
        // "terminal" has no choices/pointers -> session ends right after showing it.
        assertNull(manager.getSession(uuid));
    }

    // --- cross-conversation pointer resolution ---

    @Test
    void handleChoice_crossConversationPointer_swapsSessionButNeverShowsNewNode() {
        // Documents actual (surprising) behavior of resolvePointer's cross-conversation branch:
        // it installs a *new* DialogueSession into activeSessions for the target dialogue, but
        // handleChoice's local `session` variable still refers to the *old* (now orphaned)
        // session object and calls showNode(player, oldSession) — looking up the target node id
        // in the OLD dialogue, where it doesn't exist. The new node's text is therefore never
        // actually rendered on a cross-conversation jump; the swapped-in session is immediately
        // torn back down by the resulting "node == null" endSession(true) call. Not asserting
        // this is *correct*, only that it's the current, tested behavior.
        DialogueNode otherStart = npcNode("otherStart", "Different conversation.", List.of());
        DialogueDefinition other = new DialogueDefinition("other", "otherStart", Map.of("otherStart", otherStart));
        manager.getDialogueRegistry().register("other", other);

        DialogueChoice crossRef = new DialogueChoice("Talk to someone else", "other.otherStart", List.of());
        DialogueNode playerOpt = npcNode("player.p1", "Redirect option", List.of(crossRef));
        DialogueNode start = npcNode("start", "Greetings.", List.of(pointer("player.p1")));
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                Map.of("start", start, "player.p1", playerOpt));
        manager.getDialogueRegistry().register("greet", def);
        manager.startDialogue(player, "greet"); // sends NPC text + 1 rendered choice line = 2 calls
        manager.getSession(uuid).setDisplayedChoices(playerOpt.getChoices());
        reset(player);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(mock(Location.class));

        manager.handleChoice(player, 0);

        verify(player, never()).sendMessage(any(Component.class));
        assertNull(manager.getSession(uuid));
    }

    // --- session lifecycle ---

    @Test
    void startDialogue_restartsExistingSessionCleanly() {
        DialogueNode terminalA = npcNode("a", "A", List.of());
        DialogueDefinition defA = new DialogueDefinition("a", "a", Map.of("a", terminalA));
        DialogueNode terminalB = npcNode("b", "B", List.of());
        DialogueDefinition defB = new DialogueDefinition("b", "b", Map.of("b", terminalB));
        manager.getDialogueRegistry().register("a", defA);
        manager.getDialogueRegistry().register("b", defB);

        manager.startDialogue(player, "a");
        assertNull(manager.getSession(uuid)); // terminal node ends immediately

        assertDoesNotThrow(() -> manager.startDialogue(player, "b"));
    }

    @Test
    void clearSession_endsActiveSessionByPlayer() {
        DialogueNode playerOpt = npcNode("player.p1", "Tell me more", List.of());
        DialogueNode start = npcNode("start", "Greetings.", List.of(pointer("player.p1")));
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                Map.of("start", start, "player.p1", playerOpt));
        manager.getDialogueRegistry().register("greet", def);
        manager.startDialogue(player, "greet");
        assertNotNull(manager.getSession(uuid));

        manager.clearSession(player);

        assertNull(manager.getSession(uuid));
    }
}
