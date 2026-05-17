package org.nakii.valmora.module.npc.dialogue.intercept;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages per-player dialogue interception via PacketEvents.
 *
 * <p>SEND side — queues outgoing chat packets so other players' messages don't
 * pollute the conversation. Action-bar packets from other systems are cancelled;
 * the DialogueManager sends its own via sendBypassActionBar.
 *
 * <p>RECEIVE side — mounts the player on a client-side-only fake invisible
 * ArmorStand so the client sends PLAYER_INPUT (and STEER_VEHICLE for older
 * protocol versions) packets. These are cancelled to block movement and
 * translated into dialogue navigation on rising edge.
 */
public class ConversationPacketManager extends PacketListenerAbstract {

    private static final int HISTORY_SIZE = 100;
    // Y-offset for the fake ArmorStand seat position (1.20.2+ value from BQ reference).
    private static final double MOUNT_Y_OFFSET = -1.375;

    private final DialogueManager dialogueManager;

    private final Set<UUID> intercepting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ConcurrentLinkedQueue<Component>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Component>> history = new ConcurrentHashMap<>();
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    private final Map<UUID, InputState> prevInput = new ConcurrentHashMap<>();
    /** Client-side fake entity ID for each mounted player. */
    private final Map<UUID, Integer> mountEntityIds = new ConcurrentHashMap<>();

    public ConversationPacketManager(DialogueManager dialogueManager) {
        super(PacketListenerPriority.NORMAL);
        this.dialogueManager = dialogueManager;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        // Send dismount packets to everyone still in a conversation
        new ArrayList<>(mountEntityIds.keySet()).forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) sendDismount(p);
        });
        intercepting.clear();
        pending.clear();
        bypass.clear();
        prevInput.clear();
        mountEntityIds.clear();
    }

    // -------------------------------------------------------------------------
    // Per-player intercept start / stop
    // -------------------------------------------------------------------------

    public void startInterception(Player player) {
        UUID uuid = player.getUniqueId();
        intercepting.add(uuid);
        pending.put(uuid, new ConcurrentLinkedQueue<>());
        prevInput.put(uuid, InputState.NONE);
        sendMount(player);
    }

    public void stopInterception(Player player) {
        UUID uuid = player.getUniqueId();
        intercepting.remove(uuid);
        prevInput.remove(uuid);
        sendDismount(player);

        ConcurrentLinkedQueue<Component> queue = pending.remove(uuid);
        if (queue == null) return;

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        // Restore previous chat context so conversation text scrolls away cleanly
        Deque<Component> hist = history.get(uuid);
        if (hist != null && !hist.isEmpty()) {
            synchronized (hist) {
                int blanks = Math.max(0, HISTORY_SIZE - hist.size());
                for (int i = 0; i < blanks; i++)
                    user.sendPacketSilently(new WrapperPlayServerSystemChatMessage(false, Component.newline()));
                for (Component line : hist)
                    user.sendPacketSilently(new WrapperPlayServerSystemChatMessage(false, line));
            }
        }

        Component msg;
        while ((msg = queue.poll()) != null)
            user.sendPacketSilently(new WrapperPlayServerSystemChatMessage(false, msg));
    }

    public boolean isIntercepting(Player player) {
        return intercepting.contains(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Client-side fake ArmorStand mount via PacketEvents
    // The fake entity exists only in the client's world — no server-side entity,
    // no visibility to other players, no physics side-effects.
    // -------------------------------------------------------------------------

    private void sendMount(Player player) {
        int entityId = Bukkit.getUnsafe().nextEntityId();
        mountEntityIds.put(player.getUniqueId(), entityId);

        Location loc = player.getLocation();
        // Position the armor stand slightly below so the seated player appears at their original Y.
        Vector3d position = new Vector3d(loc.getX(), loc.getY() + MOUNT_Y_OFFSET, loc.getZ());

        var pm = PacketEvents.getAPI().getPlayerManager();

        // 1. Spawn the fake armor stand (client-side only).
        pm.sendPacket(player, new WrapperPlayServerSpawnEntity(
                entityId, Optional.empty(), EntityTypes.ARMOR_STAND,
                position, 0f, 0f, 0f, 0, Optional.empty()));

        // 2. Mark it invisible (entity flags byte, bit 5 = 0x20).
        pm.sendPacket(player, new WrapperPlayServerEntityMetadata(entityId,
                List.of(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20))));

        // 3. Make the player a passenger — this causes the client to send PLAYER_INPUT packets.
        pm.sendPacket(player, new WrapperPlayServerSetPassengers(
                entityId, new int[]{player.getEntityId()}));
    }

    private void sendDismount(Player player) {
        Integer entityId = mountEntityIds.remove(player.getUniqueId());
        if (entityId == null) return;
        // Destroying the entity automatically dismounts the player on the client side.
        PacketEvents.getAPI().getPlayerManager()
                .sendPacket(player, new WrapperPlayServerDestroyEntities(entityId));
    }

    // -------------------------------------------------------------------------
    // Bypass send — used by DialogueManager to push conversation text / action bar
    // -------------------------------------------------------------------------

    /**
     * Sends a chat message that bypasses our own interception queue so it
     * always appears immediately regardless of intercept state.
     */
    public void sendBypass(Player player, Component component) {
        UUID uuid = player.getUniqueId();
        // Set bypass flag BEFORE the null check so the fallback path is also covered.
        bypass.add(uuid);
        try {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) {
                // sendPacketSilently bypasses onPacketSend entirely.
                user.sendPacketSilently(new WrapperPlayServerSystemChatMessage(false, component));
            } else {
                player.sendMessage(component);
            }
        } finally {
            bypass.remove(uuid);
        }
    }

    /** Sends an action-bar (overlay) message that bypasses our own action-bar block. */
    public void sendBypassActionBar(Player player, Component component) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) { player.sendActionBar(component); return; }
        // sendPacketSilently bypasses onPacketSend — no bypass flag needed.
        user.sendPacketSilently(new WrapperPlayServerSystemChatMessage(true, component));
    }

    // -------------------------------------------------------------------------
    // PacketEvents — outgoing (server → client)
    // -------------------------------------------------------------------------

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!intercepting.contains(uuid)) return;
        if (bypass.contains(uuid)) return;

        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            WrapperPlayServerSystemChatMessage wrapper = new WrapperPlayServerSystemChatMessage(event);

            if (wrapper.isOverlay()) {
                // Block other systems' action-bar packets — we send our own hint.
                event.setCancelled(true);
                return;
            }

            // Record the message in the history ring-buffer.
            Component msg = wrapper.getMessage();
            Deque<Component> hist = history.computeIfAbsent(uuid, k -> new ArrayDeque<>(HISTORY_SIZE + 1));
            synchronized (hist) {
                hist.addLast(msg);
                if (hist.size() > HISTORY_SIZE) hist.removeFirst();
            }

            // Queue the message instead of delivering it now.
            ConcurrentLinkedQueue<Component> queue = pending.get(uuid);
            if (queue != null) {
                event.setCancelled(true);
                queue.add(msg);
            }
        }
    }

    // -------------------------------------------------------------------------
    // PacketEvents — incoming (client → server)
    // -------------------------------------------------------------------------

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!intercepting.contains(uuid)) return;

        // 1.21.3+ sends PLAYER_INPUT when mounted; earlier versions send STEER_VEHICLE.
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
            event.setCancelled(true);
            WrapperPlayClientPlayerInput input = new WrapperPlayClientPlayerInput(event);
            handleInput(player, uuid,
                    input.isJump(), input.isShift(), input.isForward(), input.isBackward());

        } else if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE) {
            event.setCancelled(true);
            WrapperPlayClientSteerVehicle steer = new WrapperPlayClientSteerVehicle(event);
            handleInput(player, uuid,
                    steer.isJump(), steer.isUnmount(),
                    steer.getForward() > 0f, steer.getForward() < 0f);
        }
    }

    /**
     * Translates rising-edge input signals into dialogue navigation actions.
     * Rising-edge detection prevents holding a key from firing the action 20×/s.
     */
    private void handleInput(Player player, UUID uuid,
                             boolean jump, boolean sneak, boolean forward, boolean backward) {
        InputState prev = prevInput.getOrDefault(uuid, InputState.NONE);
        prevInput.put(uuid, new InputState(jump, sneak, forward, backward));

        if (jump && !prev.jump) {
            scheduleOnMain(player, () -> {
                var session = dialogueManager.getSession(uuid);
                if (session == null) return;
                if (session.isAwaitingAutoAdvance()) dialogueManager.skipAutoAdvance(player, session);
                else dialogueManager.handleChoice(player, session.getHighlightedChoice());
            });
        } else if (sneak && !prev.sneak) {
            // Pass the Player object so endSession can call stopInterception properly.
            scheduleOnMain(player, () -> dialogueManager.clearSession(player));
        } else if (forward && !prev.forward) {
            scheduleOnMain(player, () -> {
                var session = dialogueManager.getSession(uuid);
                if (session != null) {
                    session.setHighlightedChoice(session.getHighlightedChoice() - 1);
                    dialogueManager.refreshHighlight(player, session);
                }
            });
        } else if (backward && !prev.backward) {
            scheduleOnMain(player, () -> {
                var session = dialogueManager.getSession(uuid);
                if (session != null) {
                    session.setHighlightedChoice(session.getHighlightedChoice() + 1);
                    dialogueManager.refreshHighlight(player, session);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void scheduleOnMain(Player player, Runnable task) {
        player.getScheduler().run(
                Bukkit.getPluginManager().getPlugin("Valmora"),
                scheduledTask -> task.run(),
                null);
    }

    private record InputState(boolean jump, boolean sneak, boolean forward, boolean backward) {
        static final InputState NONE = new InputState(false, false, false, false);
    }
}
