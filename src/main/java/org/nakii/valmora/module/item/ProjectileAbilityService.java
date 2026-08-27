package org.nakii.valmora.module.item;

import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges a custom projectile spawned by {@code LAUNCH_PROJECTILE} to the nested mechanics that
 * should run when it lands or strikes an entity. The projectile is keyed by its UUID; the hit
 * listener looks the callback up and executes the stored mechanic list.
 */
public final class ProjectileAbilityService {

    /**
     * @param casterId   the player who fired the projectile
     * @param onHit      mechanic maps to run against the struck entity / impact area
     * @param damage     direct damage to apply to a struck entity (0 = none)
     * @param damageType damage type for the direct hit
     * @param pierce     if true, the projectile keeps its tracking after hitting an entity (so a
     *                   piercing arrow can damage every entity along its path) instead of being
     *                   consumed on the very first hit; it's only released once the projectile
     *                   hits a block (or otherwise terminates).
     */
    public record Callback(UUID casterId, List<Map<?, ?>> onHit, double damage, String damageType, boolean pierce) {}

    private static final Map<UUID, Callback> CALLBACKS = new ConcurrentHashMap<>();

    private ProjectileAbilityService() {}

    public static void register(UUID projectileId, Callback callback) {
        CALLBACKS.put(projectileId, callback);
    }

    public static Callback consume(UUID projectileId) {
        return CALLBACKS.remove(projectileId);
    }

    /** Reads the callback without removing it — used for a piercing projectile's non-terminal (entity) hits. */
    public static Callback peek(UUID projectileId) {
        return CALLBACKS.get(projectileId);
    }

    public static boolean isTracked(LivingEntity ignored) {
        return false; // reserved for future use
    }
}
