package org.nakii.valmora.module.mob;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

import java.util.Random;

/**
 * Minimal ambient natural spawning for mob definitions flagged {@code natural-spawn: { enabled: true }}
 * in {@code mobs/*.yml}. This is deliberately simple — a periodic per-player roll, not a full
 * biome/capacity-aware spawner (see docs/ZONE_MODULE_PLAN.md's graveyard design for that larger,
 * still-unbuilt goal) — but it's a real, working alternative to the previous "mobs only ever exist
 * via /mob spawn, a zone spawner, or a script" gap.
 */
public class NaturalSpawnTask implements Runnable {

    private static final double SEARCH_RADIUS = 32.0;
    private static final int ATTEMPT_OFFSET = 24;

    private final Valmora plugin;
    private final MobManager mobManager;
    private final MobRegistry mobRegistry;
    private final Random random = new Random();

    public NaturalSpawnTask(Valmora plugin, MobManager mobManager, MobRegistry mobRegistry) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.mobRegistry = mobRegistry;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            for (String mobId : mobRegistry.getAllMobIds()) {
                MobDefinition def = mobRegistry.getMob(mobId).orElse(null);
                if (def == null || !def.isNaturalSpawn()) continue;
                if (random.nextDouble() > def.getNaturalSpawnChance()) continue;
                if (countNearby(player.getLocation(), mobId) >= def.getNaturalSpawnMaxNearby()) continue;

                Location spawnLoc = pickSpawnLocation(player.getLocation());
                if (spawnLoc != null) {
                    mobManager.spawnMob(def, spawnLoc);
                }
            }
        }
    }

    private int countNearby(Location center, String mobId) {
        int count = 0;
        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, SEARCH_RADIUS)) {
            String id = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
            if (mobId.equals(id)) count++;
        }
        return count;
    }

    /** Picks a random nearby surface block within range, or null if nothing suitable was found. */
    private Location pickSpawnLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;

        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 8 + random.nextDouble() * (ATTEMPT_OFFSET - 8);
        int x = origin.getBlockX() + (int) (Math.cos(angle) * dist);
        int z = origin.getBlockZ() + (int) (Math.sin(angle) * dist);
        int y = world.getHighestBlockYAt(x, z) + 1;

        Location candidate = new Location(world, x + 0.5, y, z + 0.5);
        if (!candidate.getBlock().getType().isAir()) return null;
        if (!candidate.clone().add(0, 1, 0).getBlock().getType().isAir()) return null;
        return candidate;
    }
}
