package org.nakii.valmora.module.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks transient combat values exposed to the scripting layer, currently the most recent
 * damage a player dealt. Backs the {@code $player.last_damage$} variable so ON_HIT abilities can
 * scale off the hit that triggered them.
 */
public final class CombatTracker {

    private static final Map<UUID, Double> LAST_DAMAGE_DEALT = new ConcurrentHashMap<>();

    private CombatTracker() {}

    public static void recordDamageDealt(UUID attacker, double amount) {
        LAST_DAMAGE_DEALT.put(attacker, amount);
    }

    public static double getLastDamageDealt(UUID attacker) {
        return LAST_DAMAGE_DEALT.getOrDefault(attacker, 0.0);
    }

    public static void clear(UUID uuid) {
        LAST_DAMAGE_DEALT.remove(uuid);
    }
}
