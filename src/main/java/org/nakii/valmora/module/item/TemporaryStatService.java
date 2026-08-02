package org.nakii.valmora.module.item;

import org.nakii.valmora.module.stat.StatManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks short-lived stat modifiers granted by item abilities (e.g. "+100 Speed for 30s").
 *
 * <p>Because {@link StatManager#recalculateStats} rebuilds the effective stat map from scratch
 * every time, temporary modifiers cannot simply be added once. Instead they are stored here and
 * re-applied on every recalculation via {@link #applyTo}, until they expire.</p>
 */
public final class TemporaryStatService {

    private record Modifier(String statId, double amount, long expiryMillis) {
        boolean isActive(long now) { return expiryMillis < 0 || now < expiryMillis; }
    }

    private static final Map<UUID, List<Modifier>> MODIFIERS = new ConcurrentHashMap<>();

    private TemporaryStatService() {}

    /**
     * Registers a temporary modifier. {@code durationSeconds < 0} means it lasts until cleared
     * (used for passive, recalculation-driven modifiers).
     */
    public static void add(UUID uuid, String statId, double amount, double durationSeconds) {
        long expiry = durationSeconds < 0 ? -1 : System.currentTimeMillis() + (long) (durationSeconds * 1000);
        MODIFIERS.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(statId.toLowerCase(), amount, expiry));
    }

    /** Applies all currently-active modifiers for the player to the given stat manager. */
    public static void applyTo(UUID uuid, StatManager statManager) {
        List<Modifier> list = MODIFIERS.get(uuid);
        if (list == null || list.isEmpty()) return;
        long now = System.currentTimeMillis();
        list.removeIf(m -> !m.isActive(now));
        for (Modifier m : list) {
            statManager.addModifier(m.statId(), m.amount());
        }
    }

    /**
     * Removes all active modifiers for a specific stat on a player. Call this before adding a
     * timed modifier to refresh rather than stack (e.g. re-casting a buff resets its timer).
     */
    public static void removeForStat(UUID uuid, String statId) {
        List<Modifier> list = MODIFIERS.get(uuid);
        if (list != null) list.removeIf(m -> m.statId().equalsIgnoreCase(statId));
    }

    /** Clears all temporary modifiers for a player (e.g. on logout or profile switch). */
    public static void clear(UUID uuid) {
        MODIFIERS.remove(uuid);
    }
}
