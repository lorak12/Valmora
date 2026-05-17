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

    public WarpDefinition(String id, String displayName, String worldName,
                          double x, double y, double z, float yaw, float pitch,
                          String unlockCondition, List<int[]> padLocations) {
        this.id = id; this.displayName = displayName; this.worldName = worldName;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        this.unlockCondition = unlockCondition; this.padLocations = padLocations;
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
}
