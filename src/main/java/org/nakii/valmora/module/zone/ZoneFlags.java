package org.nakii.valmora.module.zone;

public record ZoneFlags(
    boolean pvp,
    boolean naturalMobSpawning,
    boolean blockBreaking,
    boolean blockPlacing,
    boolean hunger,        // true = hunger depletes normally; false = cancel FoodLevelChange
    boolean entry,         // true = open to all; false = players are pushed back on entry
    boolean teleportation, // true = warps/teleport events work; false = blocked inside zone
    boolean leafDecay      // true = leaves decay normally; false = cancel LeavesDecay
) {
    public static ZoneFlags defaults() {
        return new ZoneFlags(false, false, false, false, true, true, true, true);
    }
}
