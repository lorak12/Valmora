package org.nakii.valmora.module.zone;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ZoneDefinition {
    private final String id;
    private final String displayName;
    private final String worldName;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    // Each int[] is {minX, minY, minZ, maxX, maxY, maxZ} for an additional sub-region
    private final List<int[]> extraBoxes;
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
        this(id, displayName, worldName, minX, minY, minZ, maxX, maxY, maxZ,
                List.of(), flags, fishingLootTable, mobSpawners, resourceBlocks, enterActions, exitActions);
    }

    public ZoneDefinition(String id, String displayName, String worldName,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          List<int[]> extraBoxes,
                          ZoneFlags flags, String fishingLootTable,
                          List<ZoneMobSpawner> mobSpawners,
                          Map<Material, ZoneResourceConfig> resourceBlocks,
                          List<String> enterActions, List<String> exitActions) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.extraBoxes = extraBoxes != null ? List.copyOf(extraBoxes) : List.of();
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
        if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) return true;
        for (int[] b : extraBoxes) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true;
        }
        return false;
    }

    public long volume() {
        return (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Returns all bounding boxes as lists of {minX,minY,minZ,maxX,maxY,maxZ} — primary box first. */
    public List<int[]> getAllBoxes() {
        List<int[]> all = new ArrayList<>();
        all.add(new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        all.addAll(extraBoxes);
        return all;
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
    public List<int[]> getExtraBoxes() { return extraBoxes; }
    public ZoneFlags getFlags() { return flags; }
    public boolean isPvpEnabled() { return flags.pvp(); }
    public String getFishingLootTable() { return fishingLootTable; }
    public List<ZoneMobSpawner> getMobSpawners() { return mobSpawners; }
    public Map<Material, ZoneResourceConfig> getResourceBlocks() { return resourceBlocks; }
    public List<String> getEnterActions() { return enterActions; }
    public List<String> getExitActions() { return exitActions; }

    public ZoneDefinition withFlags(ZoneFlags newFlags) {
        return new ZoneDefinition(id, displayName, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            extraBoxes, newFlags, fishingLootTable, mobSpawners, resourceBlocks, enterActions, exitActions);
    }

    public ZoneDefinition withSpawners(List<ZoneMobSpawner> newSpawners) {
        return new ZoneDefinition(id, displayName, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            extraBoxes, flags, fishingLootTable, newSpawners, resourceBlocks, enterActions, exitActions);
    }
}
