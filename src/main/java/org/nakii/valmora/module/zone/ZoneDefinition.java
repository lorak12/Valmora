package org.nakii.valmora.module.zone;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public class ZoneDefinition {
    private final String id;
    private final String displayName;
    private final String worldName;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final ZoneFlags flags;
    private final String fishingLootTable;
    private final List<ZoneMobSpawner> mobSpawners;
    private final Map<Material, ZoneResourceConfig> resourceBlocks;
    private final List<String> enterActions;
    private final List<String> exitActions;

    public ZoneDefinition(String id, String displayName, String worldName,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          ZoneFlags flags, String fishingLootTable,
                          List<ZoneMobSpawner> mobSpawners,
                          Map<Material, ZoneResourceConfig> resourceBlocks,
                          List<String> enterActions, List<String> exitActions) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.flags = flags;
        this.fishingLootTable = fishingLootTable;
        this.mobSpawners = mobSpawners;
        this.resourceBlocks = resourceBlocks;
        this.enterActions = enterActions;
        this.exitActions = exitActions;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public long volume() {
        return (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public ZoneFlags getFlags() { return flags; }
    public boolean isPvpEnabled() { return flags.pvp(); }
    public String getFishingLootTable() { return fishingLootTable; }
    public List<ZoneMobSpawner> getMobSpawners() { return mobSpawners; }
    public Map<Material, ZoneResourceConfig> getResourceBlocks() { return resourceBlocks; }
    public List<String> getEnterActions() { return enterActions; }
    public List<String> getExitActions() { return exitActions; }

    public ZoneDefinition withFlags(ZoneFlags newFlags) {
        return new ZoneDefinition(id, displayName, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            newFlags, fishingLootTable, mobSpawners, resourceBlocks, enterActions, exitActions);
    }

    public ZoneDefinition withSpawners(List<ZoneMobSpawner> newSpawners) {
        return new ZoneDefinition(id, displayName, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            flags, fishingLootTable, newSpawners, resourceBlocks, enterActions, exitActions);
    }
}
