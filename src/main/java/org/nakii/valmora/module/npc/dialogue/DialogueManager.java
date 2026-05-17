package org.nakii.valmora.module.npc.dialogue;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.npc.dialogue.intercept.ConversationPacketManager;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DialogueManager implements Listener {

    /** Ticks per character for NPC-to-NPC auto-advance delay (≈ 0.15 s / char). */
    private static final int TICKS_PER_CHAR = 3;
    private static final int MIN_AUTO_ADVANCE_TICKS = 40;
    private static final int MAX_AUTO_ADVANCE_TICKS = 200;

    private final Valmora plugin;
    private final Registry<DialogueDefinition> dialogueRegistry = new SimpleRegistry<>();
    private final Map<UUID, DialogueSession> activeSessions = new HashMap<>();
    private final Map<UUID, Integer> stopTasks = new HashMap<>();
    private final Map<UUID, Integer> actionBarTasks = new HashMap<>();

    private ConversationPacketManager packetManager;

    public DialogueManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public void setPacketManager(ConversationPacketManager packetManager) {
        this.packetManager = packetManager;
    }

    public Registry<DialogueDefinition> getDialogueRegistry() { return dialogueRegistry; }

    public DialogueSession getSession(UUID uuid) { return activeSessions.get(uuid); }

    // -------------------------------------------------------------------------
    // Chat input
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            endSession(uuid, false);
            if (packetManager != null) packetManager.stopInterception(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        DialogueSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        try {
            int choice = Integer.parseInt(raw) - 1;
            plugin.getServer().getScheduler().runTask(plugin, () -> handleChoice(player, choice));
        } catch (NumberFormatException ignored) {}
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    public void startDialogue(Player player, String dialogueId) {
        DialogueDefinition def = resolveDialogue(dialogueId);
        if (def == null) {
            player.sendMessage(Formatter.format("<red>Dialogue not found: " + dialogueId));
            return;
        }

        endSession(player, false);

        DialogueSession session = new DialogueSession(player.getUniqueId(), def);
        String startNode = pickFirstOption(player, def);
        if (startNode == null) return;
        session.setCurrentNodeId(startNode);
        activeSessions.put(player.getUniqueId(), session);

        if (packetManager != null) packetManager.startInterception(player);
        if (def.isStop()) startStopTask(player);

        clearChatDisplay(player);
        showNode(player, session);
    }

    // -------------------------------------------------------------------------
    // Choice handling
    // -------------------------------------------------------------------------

    public void handleChoice(Player player, int choiceIndex) {
        DialogueSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        List<DialogueChoice> displayed = session.getDisplayedChoices();
        if (displayed.isEmpty()) { endSession(player, true); return; }
        if (choiceIndex < 0 || choiceIndex >= displayed.size()) return;

        DialogueChoice choice = displayed.get(choiceIndex);
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        if (!choice.getEvents().isEmpty())
            plugin.getScriptModule().getEventParser().parseList(choice.getEvents()).execute(ctx);

        String next = choice.getNextNodeId();
        if (next == null || next.isBlank() || next.equalsIgnoreCase("null")) {
            endSession(player, true);
            return;
        }

        String resolvedNode = resolvePointer(player, session, next);
        if (resolvedNode == null) { endSession(player, true); return; }

        session.setCurrentNodeId(resolvedNode);
        showNode(player, session);
    }

    /**
     * Skips the current NPC-to-NPC auto-advance delay and immediately advances to the
     * pending node. Called when the player presses Space during an NPC monologue.
     */
    public void skipAutoAdvance(Player player, DialogueSession session) {
        if (!session.isAwaitingAutoAdvance()) return;
        plugin.getServer().getScheduler().cancelTask(session.getNpcAutoAdvanceTaskId());
        String next = session.getPendingNpcNodeId();
        session.clearNpcAutoAdvance();
        stopActionBarRefresh(player.getUniqueId());
        session.setCurrentNodeId(next);
        clearChatDisplay(player);
        showNode(player, session);
    }

    public void clearSession(Player player) {
        endSession(player, true);
    }

    public void clearSession(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) endSession(player, true);
        else endSession(playerUuid, false);
    }

    // -------------------------------------------------------------------------
    // Node display
    // -------------------------------------------------------------------------

    private void showNode(Player player, DialogueSession session) {
        DialogueNode node = session.getDialogue().getNode(session.getCurrentNodeId()).orElse(null);
        if (node == null) { endSession(player, true); return; }

        if (!evaluateConditions(node.getConditions(), player)) { endSession(player, true); return; }

        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        if (!node.getEvents().isEmpty())
            plugin.getScriptModule().getEventParser().parseList(node.getEvents()).execute(ctx);

        // Player-option node: transparent — execute pointer events then jump to next node
        if (node.isPlayerNode()) {
            for (DialogueChoice c : node.getChoices()) {
                if (!"__ptr__".equals(c.getText())) continue;
                if (!c.getEvents().isEmpty())
                    plugin.getScriptModule().getEventParser().parseList(c.getEvents()).execute(ctx);
                String nextId = c.getNextNodeId();
                DialogueNode nextNode = session.getDialogue().getNode(nextId).orElse(null);
                if (nextNode != null && evaluateConditions(nextNode.getConditions(), player)) {
                    session.setCurrentNodeId(nextId);
                    showNode(player, session);
                    return;
                }
            }
            endSession(player, true);
            return;
        }

        String quester = session.getDialogue().getQuesterName();

        // Separate NPC pointers from player choice pointers
        List<DialogueChoice> playerChoices = getPlayerChoices(player, node, session.getDialogue());
        List<String> npcPointers = getNpcPointers(player, node, session.getDialogue());

        // Send NPC text
        send(player, Formatter.format("<gold><bold>" + quester + " <dark_gray>▶ <reset>" + node.getText()));

        if (!playerChoices.isEmpty()) {
            session.setDisplayedChoices(playerChoices);
            renderChoices(player, session, playerChoices);
            startChoiceActionBarRefresh(player);
        } else if (!npcPointers.isEmpty()) {
            // NPC-to-NPC: schedule auto-advance with delay proportional to text length
            String nextNpc = npcPointers.get(0);
            int delay = calcAutoAdvanceDelay(node.getText());
            int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                DialogueSession s = activeSessions.get(player.getUniqueId());
                if (s == null || !s.isAwaitingAutoAdvance()) return;
                s.clearNpcAutoAdvance();
                stopActionBarRefresh(player.getUniqueId());
                s.setCurrentNodeId(nextNpc);
                clearChatDisplay(player);
                showNode(player, s);
            }, delay).getTaskId();
            session.setNpcAutoAdvance(taskId, nextNpc);
            startSkipHintRefresh(player);
        } else {
            endSession(player, true);
        }
    }

    /**
     * Re-renders the full dialogue view and immediately refreshes the action bar after
     * the player navigates choices via keyboard.
     */
    public void refreshHighlight(Player player, DialogueSession session) {
        List<DialogueChoice> choices = session.getDisplayedChoices();
        if (choices.isEmpty()) return;

        DialogueNode node = session.getDialogue().getNode(session.getCurrentNodeId()).orElse(null);
        if (node != null) {
            clearChatDisplay(player);
            String quester = session.getDialogue().getQuesterName();
            send(player, Formatter.format("<gold><bold>" + quester + " <dark_gray>▶ <reset>" + node.getText()));
            renderChoices(player, session, choices);
        }

        sendChoiceActionBar(player, session);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void send(Player player, Component component) {
        if (packetManager != null && packetManager.isIntercepting(player)) {
            packetManager.sendBypass(player, component);
        } else {
            player.sendMessage(component);
        }
    }

    private void renderChoices(Player player, DialogueSession session, List<DialogueChoice> choices) {
        int hi = session.getHighlightedChoice();
        for (int i = 0; i < choices.size(); i++) {
            DialogueChoice choice = choices.get(i);
            boolean highlighted = (i == hi);
            Component prefix = highlighted
                    ? Component.text("► [" + (i + 1) + "] ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                    : Component.text("  [" + (i + 1) + "] ", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
            Component line = prefix
                    .append(Formatter.format(choice.getText()))
                    .clickEvent(ClickEvent.runCommand("/valmora npc-choice " + i));
            send(player, line);
        }
    }

    /**
     * Returns the player-choice options for an NPC node: __ptr__ entries that point to
     * player nodes, resolved to their display text and conditions.
     */
    private List<DialogueChoice> getPlayerChoices(Player player, DialogueNode node, DialogueDefinition def) {
        List<DialogueChoice> visible = new ArrayList<>();
        for (DialogueChoice c : node.getChoices()) {
            if (!"__ptr__".equals(c.getText())) continue;
            String target = c.getNextNodeId();
            if (!target.startsWith("player.")) continue; // NPC pointer — skip here
            DialogueNode playerNode = def.getNode(target).orElse(null);
            if (playerNode == null) continue;
            if (!evaluateConditions(playerNode.getConditions(), player)) continue;
            visible.add(new DialogueChoice(playerNode.getText(), target, List.of(), List.of()));
        }
        return visible;
    }

    /**
     * Returns the NPC continuation pointers for an NPC node: __ptr__ entries that point
     * to other NPC nodes (used for auto-advance).
     */
    private List<String> getNpcPointers(Player player, DialogueNode node, DialogueDefinition def) {
        List<String> pointers = new ArrayList<>();
        for (DialogueChoice c : node.getChoices()) {
            if (!"__ptr__".equals(c.getText())) continue;
            String target = c.getNextNodeId();
            if (target.startsWith("player.")) continue; // player pointer — skip here
            DialogueNode npcNode = def.getNode(target).orElse(null);
            if (npcNode == null) continue;
            if (!evaluateConditions(npcNode.getConditions(), player)) continue;
            pointers.add(target);
        }
        return pointers;
    }

    private String pickFirstOption(Player player, DialogueDefinition def) {
        if (!def.getFirstOptions().isEmpty()) {
            for (String optionId : def.getFirstOptions()) {
                DialogueNode node = def.getNode(optionId).orElse(null);
                if (node != null && evaluateConditions(node.getConditions(), player)) return optionId;
            }
            return null;
        }
        return def.getStartNodeId();
    }

    private boolean evaluateConditions(List<String> condStrings, Player player) {
        if (condStrings == null || condStrings.isEmpty()) return true;
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        Condition group = plugin.getScriptModule().getConditionParser().parseList(condStrings);
        return group.evaluate(ctx);
    }

    private String resolvePointer(Player player, DialogueSession session, String pointer) {
        if (session.getDialogue().getNode(pointer).isPresent()) return pointer;

        // Cross-conversation: "otherDialogue.nodeId" — only if not a player.* reference
        if (pointer.contains(".") && !pointer.startsWith("player.")) {
            int dot = pointer.lastIndexOf('.');
            String convRef = pointer.substring(0, dot);
            String nodeId = pointer.substring(dot + 1);
            DialogueDefinition newDef = resolveDialogue(convRef);
            if (newDef == null) return null;
            DialogueSession newSession = new DialogueSession(player.getUniqueId(), newDef);
            activeSessions.put(player.getUniqueId(), newSession);
            return nodeId.isEmpty() ? pickFirstOption(player, newDef) : nodeId;
        }
        return null;
    }

    private DialogueDefinition resolveDialogue(String id) {
        if (id.contains(">")) {
            String convId = id.split(">", 2)[1];
            return dialogueRegistry.get(convId).orElse(null);
        }
        return dialogueRegistry.get(id).orElse(null);
    }

    // -------------------------------------------------------------------------
    // Chat clear
    // -------------------------------------------------------------------------

    private void clearChatDisplay(Player player) {
        if (packetManager == null) return;
        for (int i = 0; i < 20; i++) {
            packetManager.sendBypass(player, Component.empty());
        }
    }

    // -------------------------------------------------------------------------
    // Action bar refresh tasks
    // -------------------------------------------------------------------------

    /** Starts the repeating task that keeps the choice navigation hint on screen. */
    private void startChoiceActionBarRefresh(Player player) {
        UUID uuid = player.getUniqueId();
        stopActionBarRefresh(uuid);

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            DialogueSession s = activeSessions.get(uuid);
            if (s == null) { stopActionBarRefresh(uuid); return; }
            if (s.getDisplayedChoices().isEmpty()) return;
            sendChoiceActionBar(player, s);
        }, 0L, 40L).getTaskId();
        actionBarTasks.put(uuid, taskId);
    }

    private void sendChoiceActionBar(Player player, DialogueSession session) {
        Component bar = Component.text("[↑/↓] navigate  [Space] select  [Shift] exit", NamedTextColor.DARK_GRAY);
        if (packetManager != null) packetManager.sendBypassActionBar(player, bar);
        else player.sendActionBar(bar);
    }

    /** Starts the repeating task that shows the skip hint during NPC auto-advance. */
    private void startSkipHintRefresh(Player player) {
        UUID uuid = player.getUniqueId();
        stopActionBarRefresh(uuid);

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            DialogueSession s = activeSessions.get(uuid);
            if (s == null) { stopActionBarRefresh(uuid); return; }
            if (!s.isAwaitingAutoAdvance()) return;
            Component bar = Component.text("[Space] skip  [Shift] exit", NamedTextColor.DARK_GRAY);
            if (packetManager != null) packetManager.sendBypassActionBar(player, bar);
            else player.sendActionBar(bar);
        }, 0L, 40L).getTaskId();
        actionBarTasks.put(uuid, taskId);
    }

    private void stopActionBarRefresh(UUID uuid) {
        Integer taskId = actionBarTasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
    }

    /** Calculates auto-advance delay in ticks based on plain-text length of the message. */
    private int calcAutoAdvanceDelay(String text) {
        // Strip MiniMessage tags for length calculation
        String plain = text.replaceAll("<[^>]*>", "");
        int ticks = plain.length() * TICKS_PER_CHAR;
        return Math.max(MIN_AUTO_ADVANCE_TICKS, Math.min(MAX_AUTO_ADVANCE_TICKS, ticks));
    }

    // -------------------------------------------------------------------------
    // Stop mechanic
    // -------------------------------------------------------------------------

    private void startStopTask(Player player) {
        Location origin = player.getLocation();
        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                if (!activeSessions.containsKey(player.getUniqueId())) { cancel(); return; }
                if (player.getLocation().distanceSquared(origin) > 1.0) {
                    player.teleportAsync(origin);
                }
            }
        };
        int id = task.runTaskTimer(plugin, 5L, 5L).getTaskId();
        stopTasks.put(player.getUniqueId(), id);
    }

    // -------------------------------------------------------------------------
    // Session end
    // -------------------------------------------------------------------------

    private void endSession(Player player, boolean runFinalEvents) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = activeSessions.get(uuid);
        if (runFinalEvents && session != null && !session.getDialogue().getFinalActions().isEmpty()) {
            SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            plugin.getScriptModule().getEventParser()
                    .parseList(session.getDialogue().getFinalActions()).execute(ctx);
        }
        endSession(uuid, false);
        if (packetManager != null) packetManager.stopInterception(player);
        player.sendActionBar(Component.empty());
    }

    private void endSession(UUID uuid, boolean ignored) {
        DialogueSession session = activeSessions.remove(uuid);
        if (session != null && session.isAwaitingAutoAdvance()) {
            plugin.getServer().getScheduler().cancelTask(session.getNpcAutoAdvanceTaskId());
        }
        Integer taskId = stopTasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
        stopActionBarRefresh(uuid);
    }
}
