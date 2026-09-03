package org.nakii.valmora.module.zone;

public record ZoneFlags(
    boolean pvp,
    boolean naturalMobSpawning,
    boolean blockBreaking,
    boolean blockPlacing,
    boolean hunger,        // true = hunger depletes normally; false = cancel FoodLevelChange
    boolean entry,         // true = open to all; false = players are pushed back on entry
    boolean teleportation, // true = warps/teleport events work; false = blocked inside zone
    boolean leafDecay,     // true = leaves decay normally; false = cancel LeavesDecay
    // VANILLA_CONTROL_AUDIT.md §9 death system (docs/modules/design/death.md) — null on all three
    // below means "inherit the server-wide death.* config default", resolved by DeathPolicyResolver.
    Boolean keepInventoryOnDeath,
    Boolean keepExperienceOnDeath,
    boolean sleeping,       // true = beds usable normally; false = PlayerBedEnterEvent is cancelled
    // VANILLA_CONTROL_AUDIT.md §3 BlockFadeEvent/BlockFormEvent — true = ambient state changes
    // (ice/snow melt, water freezing, coral dying, etc.) happen normally; false = both events are
    // cancelled in the zone, e.g. to keep a snow zone's ice permanently frozen regardless of biome/
    // light conditions.
    boolean naturalBlockChanges
) {
    public static ZoneFlags defaults() {
        return new ZoneFlags(false, false, false, false, true, true, true, true, null, null, true, true);
    }
}
