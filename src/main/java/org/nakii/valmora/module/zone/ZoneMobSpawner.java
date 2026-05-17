package org.nakii.valmora.module.zone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class ZoneMobSpawner {
    private final String id;
    private final String mobId;
    private final int x;
    private final int y;
    private final int z;
    private final int spawnIntervalTicks;
    private final int maxAlive;
    private final double radius;
    private final int spawnRadius;

    public ZoneMobSpawner(String id, String mobId, int x, int y, int z,
                          int spawnIntervalTicks, int maxAlive, double radius, int spawnRadius) {
        this.id = id;
        this.mobId = mobId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnIntervalTicks = spawnIntervalTicks;
        this.maxAlive = maxAlive;
        this.radius = radius;
        this.spawnRadius = spawnRadius;
    }

    public String getId() { return id; }
    public String getMobId() { return mobId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getSpawnIntervalTicks() { return spawnIntervalTicks; }
    public int getMaxAlive() { return maxAlive; }
    public double getRadius() { return radius; }
    public int getSpawnRadius() { return spawnRadius; }

    public Location getLocation(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
