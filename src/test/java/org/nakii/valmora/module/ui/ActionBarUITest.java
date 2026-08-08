package org.nakii.valmora.module.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.npc.dialogue.DialogueChoice;
import org.nakii.valmora.module.npc.dialogue.DialogueDefinition;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.module.npc.dialogue.DialogueNode;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.stat.StatModule;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.module.stat.SystemStats;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers ActionBarUI's priority-queue behavior (fixed 2026-08-07 — see the class doc) and its
 * tick() fallthrough (active dialogue skip -> queued override -> config template -> legacy bar) —
 * previously zero coverage for the ui module (docs/IMPLEMENTATION_BACKLOG.md).
 */
class ActionBarUITest {

    private Valmora plugin;
    private ActionBarUI ui;
    private Player player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        ui = new ActionBarUI(plugin);

        uuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(mock(Location.class));
    }

    // --- showTemporary priority queue ---

    @Test
    void showTemporary_threeArgOverload_defaultsToPriorityZero() {
        ui.showTemporary(player, "hi", 20);
        when(plugin.getDialogueManager()).thenReturn(null);

        ui.tick(player);

        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void showTemporary_lowerPriority_doesNotReplaceActiveHigherPriority() {
        ui.showTemporary(player, "cooldown warning", 200, 2);
        ui.showTemporary(player, "zone entry", 200, 0); // should be dropped

        when(plugin.getDialogueManager()).thenReturn(null);
        ui.tick(player);

        verify(player).sendActionBar(argThat((Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("cooldown warning")));
    }

    @Test
    void showTemporary_equalPriority_replaces() {
        ui.showTemporary(player, "first", 200, 1);
        ui.showTemporary(player, "second", 200, 1);

        when(plugin.getDialogueManager()).thenReturn(null);
        ui.tick(player);

        verify(player).sendActionBar(argThat((Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("second")));
    }

    @Test
    void showTemporary_higherPriority_replaces() {
        ui.showTemporary(player, "ambient", 200, 0);
        ui.showTemporary(player, "urgent", 200, 2);

        when(plugin.getDialogueManager()).thenReturn(null);
        ui.tick(player);

        verify(player).sendActionBar(argThat((Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("urgent")));
    }

    @Test
    void showTemporary_expiredCurrentMessage_isReplacedRegardlessOfPriority() throws InterruptedException {
        ui.showTemporary(player, "old high priority", 1, 5); // ~50ms
        Thread.sleep(60);
        ui.showTemporary(player, "new low priority", 200, 0);

        when(plugin.getDialogueManager()).thenReturn(null);
        ui.tick(player);

        verify(player).sendActionBar(argThat((Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("new low priority")));
    }

    // --- tick() fallthrough chain ---

    @Test
    void tick_activeDialogueSession_skipsEntirely() {
        // DialogueManager itself isn't mocked here — it declares a ConversationPacketManager-typed
        // setter, and PacketEvents is a compileOnly dependency not present on the test runtime
        // classpath, so Mockito's inline mock maker can't retransform the class. A real instance
        // (never given a packet manager) with a real active session is used instead.
        DialogueManager dialogueManager = new DialogueManager(plugin);
        DialogueNode start = new DialogueNode("start", "Hi", java.util.List.of(),
                java.util.List.of(new DialogueChoice("__ptr__", "player.p1", java.util.List.of())));
        DialogueNode playerOpt = new DialogueNode("player.p1", "More", java.util.List.of(), java.util.List.of());
        DialogueDefinition def = new DialogueDefinition("greet", "start",
                java.util.Map.of("start", start, "player.p1", playerOpt));
        dialogueManager.getDialogueRegistry().register("greet", def);

        // showNode's choice-rendering path schedules a repeating action-bar-hint task.
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
        when(task.getTaskId()).thenReturn(1);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getServer()).thenReturn(server);

        // Both nodes have empty conditions/events, so startDialogue never actually needs to
        // touch plugin.getScriptModule() (evaluateConditions/event-execution short-circuit on
        // empty lists) — no ScriptModule stubbing required.
        dialogueManager.startDialogue(player, "greet"); // renders 1 choice -> session stays open

        when(plugin.getDialogueManager()).thenReturn(dialogueManager);
        reset(player); // clear the sendMessage calls startDialogue itself made
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(mock(Location.class));

        ui.showTemporary(player, "should not show", 200);
        ui.tick(player);

        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test
    void tick_expiredOverride_fallsThroughToConfigTemplate() throws InterruptedException {
        when(plugin.getDialogueManager()).thenReturn(null);
        ui.showTemporary(player, "expired", 1, 5);
        Thread.sleep(60);

        ValmoraAPI api = mock(ValmoraAPI.class);
        ScriptModule scriptModule = mock(ScriptModule.class);
        VariableResolver resolver = mock(VariableResolver.class);
        when(api.getScriptModule()).thenReturn(scriptModule);
        when(scriptModule.getVariableResolver()).thenReturn(resolver);
        when(resolver.resolveTemplate(eq("<green>Template bar"), any())).thenReturn("<green>Template bar");
        ValmoraAPI.setProvider(api);

        UIConfig config = new UIConfig("title", java.util.List.of(), "<green>Template bar", "h", "f");
        ui.setConfig(config);

        ui.tick(player);

        verify(player).sendActionBar(argThat((Component c) ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)
                        .contains("Template bar")));
    }

    @Test
    void tick_noConfigAndNoOverride_fallsBackToLegacyBar() {
        when(plugin.getDialogueManager()).thenReturn(null);

        ValmoraAPI api = mock(ValmoraAPI.class);
        PlayerManager playerManager = mock(PlayerManager.class);
        StatModule statModule = mock(StatModule.class);
        SystemStats sys = mock(SystemStats.class);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getStatModule()).thenReturn(statModule);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        when(statModule.getSystemStats()).thenReturn(sys);
        when(sys.getHealth()).thenReturn("health");
        when(sys.getDefense()).thenReturn("defense");
        when(sys.getMana()).thenReturn("mana");
        ValmoraAPI.setProvider(api);

        ValmoraProfile profile = new ValmoraProfile("Test");
        profile.getStatManager().addModifier("health", 20.0);
        profile.getStatManager().addModifier("defense", 5.0);
        profile.getStatManager().addModifier("mana", 100.0);
        ValmoraPlayer vp = new ValmoraPlayer(uuid);
        vp.addProfile(profile);
        when(playerManager.getSession(uuid)).thenReturn(vp);

        AttributeInstance maxHealthAttr = mock(AttributeInstance.class);
        when(maxHealthAttr.getValue()).thenReturn(20.0);
        when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);
        when(player.getHealth()).thenReturn(20.0);

        ui.tick(player);

        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void tick_legacyBar_noActiveProfile_isNoOp() {
        when(plugin.getDialogueManager()).thenReturn(null);

        ValmoraAPI api = mock(ValmoraAPI.class);
        PlayerManager playerManager = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(playerManager.getSession(uuid)).thenReturn(null);
        ValmoraAPI.setProvider(api);

        ui.tick(player);

        verify(player, never()).sendActionBar(any(Component.class));
    }
}
