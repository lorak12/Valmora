package org.nakii.valmora.module.zone;

public record ZoneFlags(
    boolean pvp,
    boolean naturalMobSpawning,
    boolean blockBreaking,
    boolean blockPlacing
) {
    public static ZoneFlags defaults() {
        return new ZoneFlags(false, false, false, false);
    }
}
