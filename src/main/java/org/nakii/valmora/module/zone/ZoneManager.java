package org.nakii.valmora.module.zone;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.zone.event.ZoneEnterEvent;
import org.nakii.valmora.module.zone.event.ZoneExitEvent;
import org.nakii.valmora.util.Keys;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ZoneManager {

    private final Valmora plugin;
    private final ZoneRegistry registry;
    private final Map<UUID, String> playerZones = new HashMap<>();

    // Spawner timing: key = "zoneId:spawnerId", value = tick count at last spawn
    private final Map<String, Long> spawnerLastSpawnTick = new HashMap<>();
    private long tickCount = 0;

    // Player wand selections
    private final Map<UUID, int[]> selectionPos1 = new HashMap<>();
    private final Map<UUID, int[]> selectionPos2 = new HashMap<>();
    private final Map<UUID, String> selectionWorld = new HashMap<>();

    // Visualizing players (zone borders)
    private final Set<UUID> visualizingPlayers = new HashSet<>();

    private BukkitTask spawnerTask;
    private BukkitTask mobHomeTask;
    private BukkitTask visualizationTask;
    private BukkitTask selectionTask;

    public ZoneManager(Valmora plugin, ZoneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    // ── Zone lookup ──────────────────────────────────────────────────────────

    public Optional<ZoneDefinition> getZoneAt(Location loc) {
        return registry.values().stream()
                .filter(z -> z.contains(loc))
                .min(Comparator.comparingLong(ZoneDefinition::volume));
    }

    public Optional<ZoneDefinition> getCurrentZone(Player player) {
        String id = playerZones.get(player.getUniqueId());
        if (id == null) return Optional.empty();
        return registry.get(id);
    }

    public ZoneRegistry getRegistry() { return registry; }

    // ── Player lifecycle ─────────────────────────────────────────────────────

    public void onPlayerJoin(Player player) {
        getZoneAt(player.getLocation()).ifPresent(z -> playerZones.put(player.getUniqueId(), z.getId()));
    }

    public void onPlayerQuit(UUID uuid) {
        playerZones.remove(uuid);
        visualizingPlayers.remove(uuid);
    }

    public void checkTransition(Player player) {
        String oldZoneId = playerZones.get(player.getUniqueId());
        Optional<ZoneDefinition> newZone = getZoneAt(player.getLocation());
        String newZoneId = newZone.map(ZoneDefinition::getId).orElse(null);

        if (Objects.equals(oldZoneId, newZoneId)) return;

        if (oldZoneId != null)
            registry.get(oldZoneId).ifPresent(z ->
                    plugin.getServer().getPluginManager().callEvent(new ZoneExitEvent(player, z)));

        if (newZoneId != null) {
            playerZones.put(player.getUniqueId(), newZoneId);
            plugin.getServer().getPluginManager().callEvent(new ZoneEnterEvent(player, newZone.get()));
        } else {
            playerZones.remove(player.getUniqueId());
        }
    }

    // ── Spawner task ─────────────────────────────────────────────────────────

    public void startSpawnerTask() {
        if (spawnerTask != null) { spawnerTask.cancel(); spawnerTask = null; }
        spawnerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSpawners, 20L, 20L);
    }

    public void stopSpawnerTask() {
        if (spawnerTask != null) { spawnerTask.cancel(); spawnerTask = null; }
    }

    private void tickSpawners() {
        tickCount += 20;
        for (ZoneDefinition zone : registry.values()) {
            for (ZoneMobSpawner spawner : zone.getMobSpawners()) {
                String key = zone.getId() + ":" + spawner.getId();
                long lastSpawn = spawnerLastSpawnTick.getOrDefault(key, 0L);
                if (tickCount - lastSpawn < spawner.getSpawnIntervalTicks()) continue;

                World world = Bukkit.getWorld(zone.getWorldName());
                if (world == null) continue;

                Location center = new Location(world, spawner.getX() + 0.5, spawner.getY(), spawner.getZ() + 0.5);
                int alive = countMobs(center, spawner.getMobId(), spawner.getRadius());
                if (alive >= spawner.getMaxAlive()) continue;

                MobDefinition def = plugin.getMobManager().getMobDefinition(spawner.getMobId());
                if (def == null) continue;

                Location spawnLoc = findSafeSpawnLocation(world, spawner.getX(), spawner.getY(), spawner.getZ(), spawner.getSpawnRadius());
                LivingEntity entity = plugin.getMobManager().spawnMob(def, spawnLoc);
                if (entity != null) {
                    // Tag with home and wander radius so the behavior task can use them
                    int wanderRadius = Math.max(spawner.getSpawnRadius() * 2, 4);
                    entity.getPersistentDataContainer().set(Keys.MOB_HOME_KEY, PersistentDataType.STRING,
                        spawner.getX() + "," + spawner.getY() + "," + spawner.getZ()
                            + "," + wanderRadius + "," + world.getName());
                    spawnerLastSpawnTick.put(key, tickCount);
                }
            }
        }
    }

    private int countMobs(Location center, String mobId, double radius) {
        if (center.getWorld() == null) return 0;
        int count = 0;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le) {
                String id = le.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
                if (mobId.equalsIgnoreCase(id)) count++;
            }
        }
        return count;
    }

    private Location findSafeSpawnLocation(World world, int cx, int cy, int cz, int spawnRadius) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int range = Math.max(spawnRadius, 1);
        for (int attempt = 0; attempt < 20; attempt++) {
            int ox = rng.nextInt(range * 2 + 1) - range;
            int oz = rng.nextInt(range * 2 + 1) - range;
            int x = cx + ox, z = cz + oz;

            // Search Y levels from top to bottom of the ±range band
            for (int dy = range; dy >= -range; dy--) {
                int y = cy + dy;
                if (y < 1 || y > world.getMaxHeight() - 2) continue;
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                if (!ground.getType().isSolid()) continue;
                if (ground.isLiquid()) continue;
                if (feet.getType() != Material.AIR) continue;
                if (head.getType() != Material.AIR) continue;
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                // Skip if another living entity is already occupying this spot
                if (world.getNearbyEntities(loc, 0.8, 0.8, 0.8).stream()
                        .anyMatch(e -> e instanceof LivingEntity && !(e instanceof Player))) continue;
                return loc;
            }
        }
        // Fallback: exact spawner location
        return new Location(world, cx + 0.5, cy, cz + 0.5);
    }

    // ── Mob home task ────────────────────────────────────────────────────────

    public void startMobHomeTask() {
        if (mobHomeTask != null) { mobHomeTask.cancel(); mobHomeTask = null; }
        mobHomeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickMobHomes, 40L, 40L);
    }

    public void stopMobHomeTask() {
        if (mobHomeTask != null) { mobHomeTask.cancel(); mobHomeTask = null; }
    }

    private void tickMobHomes() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Mob mob)) continue;

                String homeStr = entity.getPersistentDataContainer().get(Keys.MOB_HOME_KEY, PersistentDataType.STRING);
                if (homeStr == null) continue;

                String[] parts = homeStr.split(",");
                if (parts.length != 5) continue; // hx,hy,hz,wanderRadius,world

                try {
                    int hx = Integer.parseInt(parts[0]);
                    int hy = Integer.parseInt(parts[1]);
                    int hz = Integer.parseInt(parts[2]);
                    int wanderRadius = Integer.parseInt(parts[3]);
                    String worldName = parts[4];
                    if (!world.getName().equals(worldName)) continue;

                    Location home = new Location(world, hx + 0.5, hy, hz + 0.5);
                    var pathfinder = mob.getPathfinder();

                    // Zone containment: if mob left its zone, cancel target and return to home
                    Optional<ZoneDefinition> homeZone = getZoneAt(home);
                    if (homeZone.isPresent() && !homeZone.get().contains(mob.getLocation())) {
                        mob.setTarget(null);
                        pathfinder.stopPathfinding();
                        pathfinder.moveTo(home, 1.3);
                        continue;
                    }

                    // Wander: periodically give idle mobs (no target, no active path) a nearby destination
                    if (mob.getTarget() == null && !pathfinder.hasPath() && rng.nextInt(4) == 0) {
                        Location target = findSafeSpawnLocation(world, hx, hy, hz, wanderRadius);
                        // Only wander to destinations that stay within the zone
                        if (homeZone.isEmpty() || homeZone.get().contains(target)) {
                            pathfinder.moveTo(target, 0.6 + rng.nextDouble() * 0.3);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ── Wand selection ───────────────────────────────────────────────────────

    public void setPos1(Player player, int x, int y, int z) {
        UUID uuid = player.getUniqueId();
        selectionPos1.put(uuid, new int[]{x, y, z});
        selectionWorld.put(uuid, player.getWorld().getName());
    }

    public void setPos2(Player player, int x, int y, int z) {
        UUID uuid = player.getUniqueId();
        selectionPos2.put(uuid, new int[]{x, y, z});
        selectionWorld.put(uuid, player.getWorld().getName());
    }

    public void clearSelection(Player player) {
        UUID uuid = player.getUniqueId();
        selectionPos1.remove(uuid);
        selectionPos2.remove(uuid);
        selectionWorld.remove(uuid);
    }

    public int[] getPos1(UUID uuid) { return selectionPos1.get(uuid); }
    public int[] getPos2(UUID uuid) { return selectionPos2.get(uuid); }
    public String getSelectionWorld(UUID uuid) { return selectionWorld.get(uuid); }

    public boolean hasFullSelection(UUID uuid) {
        return selectionPos1.containsKey(uuid) && selectionPos2.containsKey(uuid);
    }

    // ── Zone CRUD ────────────────────────────────────────────────────────────

    public ZoneDefinition createZone(String id, String displayName, int x1, int y1, int z1,
                                     int x2, int y2, int z2, String worldName) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        ZoneFlags flags = ZoneFlags.defaults();
        ZoneDefinition zone = new ZoneDefinition(id, displayName, worldName,
            minX, minY, minZ, maxX, maxY, maxZ, List.of(), flags, null,
            new ArrayList<>(), Map.of(), new ArrayList<>(), new ArrayList<>());
        registry.register(id, zone);
        saveZoneToFile(zone);
        return zone;
    }

    public boolean deleteZone(String id) {
        ZoneDefinition zone = registry.get(id).orElse(null);
        if (zone == null) return false;
        registry.unregister(id);
        File file = new File(plugin.getDataFolder(), "zones/" + id + ".yml");
        if (file.exists()) file.delete();
        return true;
    }

    public ZoneDefinition setZoneFlags(String id, ZoneFlags flags) {
        ZoneDefinition zone = registry.get(id).orElse(null);
        if (zone == null) return null;
        ZoneDefinition updated = zone.withFlags(flags);
        registry.register(id, updated);
        saveZoneToFile(updated);
        return updated;
    }

    public ZoneDefinition addSpawner(String zoneId, ZoneMobSpawner spawner) {
        ZoneDefinition zone = registry.get(zoneId).orElse(null);
        if (zone == null) return null;
        List<ZoneMobSpawner> spawners = new ArrayList<>(zone.getMobSpawners());
        spawners.add(spawner);
        ZoneDefinition updated = zone.withSpawners(spawners);
        registry.register(zoneId, updated);
        saveZoneToFile(updated);
        return updated;
    }

    public boolean removeSpawner(String zoneId, String spawnerId) {
        ZoneDefinition zone = registry.get(zoneId).orElse(null);
        if (zone == null) return false;
        List<ZoneMobSpawner> spawners = new ArrayList<>(zone.getMobSpawners());
        boolean removed = spawners.removeIf(s -> s.getId().equalsIgnoreCase(spawnerId));
        if (removed) {
            ZoneDefinition updated = zone.withSpawners(spawners);
            registry.register(zoneId, updated);
            saveZoneToFile(updated);
            spawnerLastSpawnTick.remove(zoneId + ":" + spawnerId);
        }
        return removed;
    }

    public void saveZoneToFile(ZoneDefinition zone) {
        File dir = new File(plugin.getDataFolder(), "zones");
        dir.mkdirs();
        File file = new File(dir, zone.getId() + ".yml");

        YamlConfiguration config = new YamlConfiguration();
        String sec = zone.getId();
        config.set(sec + ".display-name", zone.getDisplayName());
        config.set(sec + ".world", zone.getWorldName());
        config.set(sec + ".min", List.of(zone.getMinX(), zone.getMinY(), zone.getMinZ()));
        config.set(sec + ".max", List.of(zone.getMaxX(), zone.getMaxY(), zone.getMaxZ()));
        config.set(sec + ".allow.pvp", zone.getFlags().pvp());
        config.set(sec + ".allow.natural-mob-spawning", zone.getFlags().naturalMobSpawning());
        config.set(sec + ".allow.block-breaking", zone.getFlags().blockBreaking());
        config.set(sec + ".allow.block-placing", zone.getFlags().blockPlacing());
        config.set(sec + ".allow.hunger", zone.getFlags().hunger());
        config.set(sec + ".allow.entry", zone.getFlags().entry());
        config.set(sec + ".allow.teleportation", zone.getFlags().teleportation());
        config.set(sec + ".allow.leaf-decay", zone.getFlags().leafDecay());

        if (!zone.getExtraBoxes().isEmpty()) {
            List<Map<String, List<Integer>>> boxEntries = new ArrayList<>();
            for (int[] b : zone.getExtraBoxes()) {
                boxEntries.add(Map.of(
                    "min", List.of(b[0], b[1], b[2]),
                    "max", List.of(b[3], b[4], b[5])
                ));
            }
            config.set(sec + ".extra-boxes", boxEntries);
        }

        int i = 0;
        for (ZoneMobSpawner spawner : zone.getMobSpawners()) {
            String spawnerId = spawner.getId().isEmpty() ? "spawner_" + i : spawner.getId();
            String path = sec + ".mob-spawners." + spawnerId;
            config.set(path + ".mob", spawner.getMobId());
            config.set(path + ".x", spawner.getX());
            config.set(path + ".y", spawner.getY());
            config.set(path + ".z", spawner.getZ());
            config.set(path + ".spawn-interval", spawner.getSpawnIntervalTicks());
            config.set(path + ".max-alive", spawner.getMaxAlive());
            config.set(path + ".radius", spawner.getRadius());
            config.set(path + ".spawn-radius", spawner.getSpawnRadius());
            i++;
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Zones] Failed to save zone '" + zone.getId() + "': " + e.getMessage());
        }
    }

    // ── Zone border visualization ────────────────────────────────────────────

    public void startVisualizationTask() {
        if (visualizationTask != null) { visualizationTask.cancel(); visualizationTask = null; }
        visualizationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVisualization, 40L, 40L);
    }

    public void stopVisualizationTask() {
        if (visualizationTask != null) { visualizationTask.cancel(); visualizationTask = null; }
        visualizingPlayers.clear();
    }

    /** Returns the new state (true = on, false = off). */
    public boolean toggleVisualization(Player player) {
        UUID uuid = player.getUniqueId();
        if (visualizingPlayers.remove(uuid)) return false;
        visualizingPlayers.add(uuid);
        return true;
    }

    private void tickVisualization() {
        if (visualizingPlayers.isEmpty()) return;
        for (UUID uuid : new HashSet<>(visualizingPlayers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) { visualizingPlayers.remove(uuid); continue; }
            String world = player.getWorld().getName();
            for (ZoneDefinition zone : registry.values()) {
                if (!zone.getWorldName().equals(world)) continue;
                // Only render if player is within 200 blocks of the primary box center
                double cx = (zone.getMinX() + zone.getMaxX()) / 2.0;
                double cy = (zone.getMinY() + zone.getMaxY()) / 2.0;
                double cz = (zone.getMinZ() + zone.getMaxZ()) / 2.0;
                if (player.getLocation().distanceSquared(new Location(player.getWorld(), cx, cy, cz)) > 200 * 200) continue;
                for (int[] b : zone.getAllBoxes()) {
                    drawBox(player, b[0], b[1], b[2], b[3], b[4], b[5], Color.YELLOW);
                }
            }
        }
    }

    // ── Selection visualization task ─────────────────────────────────────────

    public void startSelectionTask() {
        if (selectionTask != null) { selectionTask.cancel(); selectionTask = null; }
        selectionTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSelectionVisualization, 10L, 10L);
    }

    public void stopSelectionTask() {
        if (selectionTask != null) { selectionTask.cancel(); selectionTask = null; }
    }

    private void tickSelectionVisualization() {
        for (UUID uuid : new ArrayList<>(selectionPos1.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            String world = selectionWorld.getOrDefault(uuid, "");
            if (!player.getWorld().getName().equals(world)) continue;

            int[] p1 = selectionPos1.get(uuid);
            int[] p2 = selectionPos2.get(uuid);

            if (p1 != null) drawPoint(player, p1[0], p1[1], p1[2], Color.BLUE);
            if (p2 != null) drawPoint(player, p2[0], p2[1], p2[2], Color.RED);

            if (p1 != null && p2 != null) {
                drawBox(player,
                        Math.min(p1[0], p2[0]), Math.min(p1[1], p2[1]), Math.min(p1[2], p2[2]),
                        Math.max(p1[0], p2[0]), Math.max(p1[1], p2[1]), Math.max(p1[2], p2[2]),
                        Color.GREEN);
            }
        }
    }

    // ── Particle helpers ─────────────────────────────────────────────────────

    private void drawPoint(Player player, int x, int y, int z, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        for (double dx = 0; dx <= 1; dx += 0.5) {
            for (double dy = 0; dy <= 1; dy += 0.5) {
                for (double dz = 0; dz <= 1; dz += 0.5) {
                    player.spawnParticle(Particle.DUST, x + dx, y + dy, z + dz, 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private void drawBox(Player player, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        double x1 = minX, y1 = minY, z1 = minZ;
        double x2 = maxX + 1, y2 = maxY + 1, z2 = maxZ + 1;
        // Bottom
        drawLine(player, x1, y1, z1, x2, y1, z1, dust);
        drawLine(player, x2, y1, z1, x2, y1, z2, dust);
        drawLine(player, x2, y1, z2, x1, y1, z2, dust);
        drawLine(player, x1, y1, z2, x1, y1, z1, dust);
        // Top
        drawLine(player, x1, y2, z1, x2, y2, z1, dust);
        drawLine(player, x2, y2, z1, x2, y2, z2, dust);
        drawLine(player, x2, y2, z2, x1, y2, z2, dust);
        drawLine(player, x1, y2, z2, x1, y2, z1, dust);
        // Verticals
        drawLine(player, x1, y1, z1, x1, y2, z1, dust);
        drawLine(player, x2, y1, z1, x2, y2, z1, dust);
        drawLine(player, x2, y1, z2, x2, y2, z2, dust);
        drawLine(player, x1, y1, z2, x1, y2, z2, dust);
    }

    private void drawLine(Player player, double x1, double y1, double z1,
                          double x2, double y2, double z2, Particle.DustOptions dust) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.1) return;
        int steps = Math.min((int) Math.ceil(len), 64);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            player.spawnParticle(Particle.DUST, x1 + dx * t, y1 + dy * t, z1 + dz * t, 1, 0, 0, 0, 0, dust);
        }
    }

    public Set<UUID> getVisualizingPlayers() { return Collections.unmodifiableSet(visualizingPlayers); }
}
