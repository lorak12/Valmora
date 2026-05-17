package org.nakii.valmora.module.npc;

import com.destroystokyo.paper.SkinParts;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.module.npc.event.NpcInteractEvent;
import org.nakii.valmora.module.script.condition.ConditionGroup;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcManager {

    private final Valmora plugin;
    private final Registry<NpcDefinition> registry;
    private final DialogueManager dialogueManager;

    private final Map<String, UUID> npcEntityMap = new HashMap<>();
    private final Map<UUID, String> entityNpcMap = new HashMap<>();
    private final Map<String, UUID> npcTagMap = new HashMap<>();
    /** npcId → (hologramName → TextDisplay uuid) */
    private final Map<String, Map<String, UUID>> holoEntityMap = new HashMap<>();
    /** "npcId:hologramName" → repeating check task */
    private final Map<String, BukkitTask> holoTasks = new HashMap<>();

    private BukkitTask respawnTask;
    private BukkitTask lookTask;
    private static final double LOOK_RANGE = 10.0;

    public NpcManager(Valmora plugin, Registry<NpcDefinition> registry, DialogueManager dialogueManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.dialogueManager = dialogueManager;
    }

    public Registry<NpcDefinition> getRegistry() { return registry; }
    public DialogueManager getDialogueManager() { return dialogueManager; }

    public void spawnAll() {
        cleanupStaleEntities();
        for (NpcDefinition def : registry.values()) spawnNpc(def);
    }

    public void despawnAll() {
        npcEntityMap.values().forEach(uuid -> { Entity e = Bukkit.getEntity(uuid); if (e != null) e.remove(); });
        npcTagMap.values().forEach(uuid -> { Entity e = Bukkit.getEntity(uuid); if (e != null) e.remove(); });
        holoEntityMap.values().forEach(holoMap ->
                holoMap.values().forEach(uuid -> { Entity e = Bukkit.getEntity(uuid); if (e != null) e.remove(); }));
        holoTasks.values().forEach(BukkitTask::cancel);
        npcEntityMap.clear();
        entityNpcMap.clear();
        npcTagMap.clear();
        holoEntityMap.clear();
        holoTasks.clear();
    }

    public void registerAndSpawn(NpcDefinition def) {
        registry.register(def.getId(), def);
        spawnNpc(def);
    }

    private void cleanupStaleEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String npcId = entity.getPersistentDataContainer().get(Keys.NPC_ID_KEY, PersistentDataType.STRING);
                if (npcId != null) entity.remove();
            }
        }
    }

    private boolean spawnNpc(NpcDefinition def) {
        World world = Bukkit.getWorld(def.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("[NPC] World '" + def.getWorldName() + "' not loaded, cannot spawn '" + def.getId() + "'.");
            return false;
        }

        Location loc = new Location(world, def.getX(), def.getY(), def.getZ(), def.getYaw(), 0f);

        if (def.getEntityType() == EntityType.MANNEQUIN) {
            return spawnMannequin(def, world, loc);
        }

        Entity spawned;
        try { spawned = world.spawnEntity(loc, def.getEntityType()); }
        catch (Exception e) { plugin.getLogger().warning("[NPC] Failed to spawn '" + def.getId() + "': " + e.getMessage()); return false; }

        if (!(spawned instanceof LivingEntity entity)) { spawned.remove(); return false; }
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(Keys.NPC_ID_KEY, PersistentDataType.STRING, def.getId());

        npcEntityMap.put(def.getId(), entity.getUniqueId());
        entityNpcMap.put(entity.getUniqueId(), def.getId());

        if (def.isShowName()) {
            spawnNameTag(def, world, def.getY() + entity.getHeight() + 0.3);
        }
        spawnHolograms(def, world);
        return true;
    }

    private boolean spawnMannequin(NpcDefinition def, World world, Location loc) {
        try {
            double[] heightHolder = {1.8};
            Mannequin mannequin = world.spawn(loc, Mannequin.class, entity -> {
                entity.setImmovable(true);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setRemoveWhenFarAway(false);
                entity.setPersistent(false);
                entity.setCustomNameVisible(false);
                entity.setSkinParts(SkinParts.allParts());
                entity.getPersistentDataContainer().set(Keys.NPC_ID_KEY, PersistentDataType.STRING, def.getId());
                heightHolder[0] = entity.getHeight();

                if (def.getSkinTexture() != null) {
                    String profileName = def.getId().length() <= 16 ? def.getId() : def.getId().substring(0, 16);
                    UUID profileUuid = UUID.nameUUIDFromBytes(("npc:" + def.getId()).getBytes(StandardCharsets.UTF_8));
                    ProfileProperty textureProp = new ProfileProperty("textures", def.getSkinTexture(), def.getSkinSignature());
                    ResolvableProfile profile = ResolvableProfile.resolvableProfile()
                            .uuid(profileUuid)
                            .name(profileName)
                            .addProperty(textureProp)
                            .build();
                    entity.setProfile(profile);
                }
            });

            npcEntityMap.put(def.getId(), mannequin.getUniqueId());
            entityNpcMap.put(mannequin.getUniqueId(), def.getId());
            if (def.isShowName()) {
                spawnNameTag(def, world, def.getY() + heightHolder[0] + 0.3);
            }
            spawnHolograms(def, world);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[NPC] Failed to spawn mannequin '" + def.getId() + "': " + e.getMessage());
            return false;
        }
    }

    private void spawnNameTag(NpcDefinition def, World world, double tagY) {
        Location tagLoc = new Location(world, def.getX(), tagY, def.getZ());
        Component nameComponent = Formatter.format(def.getDisplayName());
        TextDisplay td = world.spawn(tagLoc, TextDisplay.class, display -> {
            display.text(nameComponent);
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setPersistent(false);
            display.getPersistentDataContainer().set(Keys.NPC_ID_KEY, PersistentDataType.STRING, def.getId());
        });
        npcTagMap.put(def.getId(), td.getUniqueId());
    }

    // ── Hologram management ───────────────────────────────────────────────────

    /** Y origin for holograms: 2 blocks above the NPC's feet (top of head). */
    private static final double HOLO_ORIGIN_Y = 2.0;

    private void spawnHolograms(NpcDefinition def, World world) {
        despawnHolograms(def.getId());
        for (HologramDefinition holo : def.getHolograms()) {
            ConditionGroup conditions = plugin.getScriptModule().getConditionParser().parseList(holo.getConditions());
            // Apply immediately so the hologram is visible as soon as the NPC spawns.
            applyHologramVisibility(def, world, holo, conditions);
            scheduleHologramTask(def, holo, conditions);
        }
    }

    private void scheduleHologramTask(NpcDefinition def, HologramDefinition holo, ConditionGroup conditions) {
        String taskKey = def.getId() + ":" + holo.getName();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World w = Bukkit.getWorld(def.getWorldName());
            if (w == null) return;
            applyHologramVisibility(def, w, holo, conditions);
        }, holo.getCheckInterval(), holo.getCheckInterval());

        holoTasks.put(taskKey, task);
    }

    private void applyHologramVisibility(NpcDefinition def, World world, HologramDefinition holo, ConditionGroup conditions) {
        boolean shouldShow;
        try {
            Location npcLoc = new Location(world, def.getX(), def.getY(), def.getZ());
            shouldShow = conditions.evaluate(new SimpleExecutionContext(null, npcLoc, null));
        } catch (Exception e) {
            shouldShow = false;
        }

        Map<String, UUID> npcHolos = holoEntityMap.computeIfAbsent(def.getId(), k -> new HashMap<>());
        UUID existingUuid = npcHolos.get(holo.getName());
        Entity existing = existingUuid != null ? Bukkit.getEntity(existingUuid) : null;

        if (shouldShow && existing == null) {
            Location holoLoc = new Location(world,
                    def.getX() + holo.getOffsetX(),
                    def.getY() + HOLO_ORIGIN_Y + holo.getOffsetY(),
                    def.getZ() + holo.getOffsetZ());
            TextDisplay td = world.spawn(holoLoc, TextDisplay.class, display -> {
                display.text(Formatter.format(holo.getText()));
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setDefaultBackground(false);
                display.setPersistent(false);
                display.getPersistentDataContainer().set(Keys.NPC_ID_KEY, PersistentDataType.STRING, def.getId());
            });
            npcHolos.put(holo.getName(), td.getUniqueId());
        } else if (!shouldShow && existing != null) {
            existing.remove();
            npcHolos.remove(holo.getName());
        }
    }

    private void despawnHolograms(String npcId) {
        Map<String, UUID> holos = holoEntityMap.remove(npcId);
        if (holos != null) {
            holos.values().forEach(uuid -> {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null) e.remove();
            });
        }
        String prefix = npcId + ":";
        holoTasks.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().cancel();
                return true;
            }
            return false;
        });
    }

    // ── Spawn / despawn helpers ───────────────────────────────────────────────

    /** Despawns a single NPC entity, its name tag, and its holograms without touching the registry. */
    public void despawnNpc(String id) {
        UUID uuid = npcEntityMap.remove(id);
        if (uuid != null) {
            entityNpcMap.remove(uuid);
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
        UUID tagUuid = npcTagMap.remove(id);
        if (tagUuid != null) {
            Entity t = Bukkit.getEntity(tagUuid);
            if (t != null) t.remove();
        }
        despawnHolograms(id);
    }

    /** Removes an NPC from the registry and despawns it. */
    public void removeNpc(String id) {
        despawnNpc(id);
        registry.unregister(id);
    }

    /** Updates a definition in the registry and respawns the entity. Returns false if spawn failed. */
    public boolean updateAndRespawn(NpcDefinition def) {
        despawnNpc(def.getId());
        registry.register(def.getId(), def);
        return spawnNpc(def);
    }

    /** Returns the live world location of a spawned NPC, or null if not currently spawned. */
    public Location getSpawnedLocation(String id) {
        UUID uuid = npcEntityMap.get(id);
        if (uuid == null) return null;
        Entity e = Bukkit.getEntity(uuid);
        return e != null ? e.getLocation() : null;
    }

    // ── Background tasks ──────────────────────────────────────────────────────

    public void startRespawnTask() {
        if (respawnTask != null) respawnTask.cancel();
        respawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkRespawn, 1200L, 1200L);
    }

    public void stopRespawnTask() {
        if (respawnTask != null) { respawnTask.cancel(); respawnTask = null; }
    }

    public void startLookTask() {
        if (lookTask != null) lookTask.cancel();
        lookTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, UUID> entry : npcEntityMap.entrySet()) {
                NpcDefinition def = registry.get(entry.getKey()).orElse(null);
                if (def == null || !def.isLookAtPlayer()) continue;
                Entity e = Bukkit.getEntity(entry.getValue());
                if (!(e instanceof LivingEntity npc)) continue;
                Player nearest = null;
                double nearestDistSq = LOOK_RANGE * LOOK_RANGE;
                for (Player p : npc.getWorld().getPlayers()) {
                    double d = p.getLocation().distanceSquared(npc.getLocation());
                    if (d < nearestDistSq) { nearestDistSq = d; nearest = p; }
                }
                if (nearest != null) npc.lookAt(nearest.getEyeLocation(), LookAnchor.EYES);
            }
        }, 5L, 5L);
    }

    public void stopLookTask() {
        if (lookTask != null) { lookTask.cancel(); lookTask = null; }
    }

    private void checkRespawn() {
        for (NpcDefinition def : registry.values()) {
            UUID uuid = npcEntityMap.get(def.getId());
            if (uuid == null || Bukkit.getEntity(uuid) == null) {
                entityNpcMap.remove(uuid);
                npcEntityMap.remove(def.getId());
                UUID tagUuid = npcTagMap.remove(def.getId());
                if (tagUuid != null) { Entity t = Bukkit.getEntity(tagUuid); if (t != null) t.remove(); }
                despawnHolograms(def.getId());
                spawnNpc(def);
            }
        }
    }

    // ── Interaction handlers ──────────────────────────────────────────────────

    public void handleRightClick(Player player, String npcId) {
        NpcDefinition def = registry.get(npcId).orElse(null);
        if (def == null) return;
        plugin.getServer().getPluginManager().callEvent(new NpcInteractEvent(player, def));

        if (def.getBoundConversationId() != null && !def.getBoundConversationId().isEmpty()) {
            dialogueManager.startDialogue(player, def.getBoundConversationId());
            return;
        }
        if (!def.getOnRightClick().isEmpty()) {
            SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            plugin.getScriptModule().getEventParser().parseList(def.getOnRightClick()).execute(ctx);
        }
    }

    public void handleLeftClick(Player player, String npcId) {
        NpcDefinition def = registry.get(npcId).orElse(null);
        if (def == null) return;
        if (!def.getOnLeftClick().isEmpty()) {
            SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            plugin.getScriptModule().getEventParser().parseList(def.getOnLeftClick()).execute(ctx);
        }
    }
}
