package org.nakii.valmora.module.warp;

import java.util.List;

public class WarpDefinition {
    private final String id;
    private final String displayName;
    private final String worldName;
    private final double x, y, z;
    private final float yaw, pitch;
    private final String unlockCondition;
    private final List<int[]> padLocations;
    private final double cost;
    private final int cooldownSeconds;
    private final int warmupSeconds;
    private final String permission;

    public WarpDefinition(String id, String displayName, String worldName,
                          double x, double y, double z, float yaw, float pitch,
                          String unlockCondition, List<int[]> padLocations) {
        this(id, displayName, worldName, x, y, z, yaw, pitch, unlockCondition, padLocations,
                0.0, 0, 0, null);
    }

    public WarpDefinition(String id, String displayName, String worldName,
                          double x, double y, double z, float yaw, float pitch,
                          String unlockCondition, List<int[]> padLocations,
                          double cost, int cooldownSeconds, int warmupSeconds, String permission) {
        this.id = id; this.displayName = displayName; this.worldName = worldName;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        this.unlockCondition = unlockCondition; this.padLocations = padLocations;
        this.cost = cost; this.cooldownSeconds = cooldownSeconds; this.warmupSeconds = warmupSeconds;
        this.permission = permission;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getUnlockCondition() { return unlockCondition; }
    public List<int[]> getPadLocations() { return padLocations; }

    /** Coin cost deducted from the player's purse on use — 0 (default) means free. */
    public double getCost() { return cost; }
    /** Per-player, per-warp reuse delay in seconds — 0 (default) means no cooldown. */
    public int getCooldownSeconds() { return cooldownSeconds; }
    /** Cast-time delay before the teleport fires, cancelled by movement/damage — 0 (default) means instant. */
    public int getWarmupSeconds() { return warmupSeconds; }
    /** Permission node required to use this warp, or {@code null} for none. */
    public String getPermission() { return permission; }
}
