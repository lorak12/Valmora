package org.nakii.valmora.module.enchant.state;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker for {@code state.transient} counters, keyed per attacker (not globally per
 * victim) — the concrete fix for the pre-overhaul {@code LethalityLogic}'s stacking bug, where the
 * stack count was keyed only by victim UUID and so was shared/polluted across every attacker
 * hitting the same target. Each entry also remembers which victim it was last recorded against, so
 * {@link TransientStateDefinition#resetOnTargetSwitch()} can reset the count the moment that
 * attacker switches target, without needing a separate per-(attacker, victim) entry per target ever
 * fought.
 *
 * <p>{@link #cleanup()} purges entries idle past a generous fixed window — the concrete fix for the
 * pre-overhaul logic classes' unbounded map growth (an attacker who stops fighting, logs off, or is
 * simply never fought by anyone again previously stayed in the map forever).
 */
public class TransientStateTracker {

    /** Entries idle longer than this are purged by {@link #cleanup()}, independent of any one
     *  enchant's own (usually much shorter) {@code reset-after-seconds}. */
    private static final long CLEANUP_IDLE_MILLIS = 15 * 60 * 1000L;

    private record TrackerKey(UUID attacker, String enchantId, String stateKey) {
    }

    private static final class Entry {
        UUID victim;
        int count;
        long lastMutateMillis;
    }

    private final Map<TrackerKey, Entry> entries = new ConcurrentHashMap<>();

    /** Applies {@code op}/{@code amount} to the counter for {@code (attacker, enchantId, key)},
     *  first applying any reset rules {@code def} declares, and returns the resulting value.
     *  {@code attacker == null} is a no-op returning 0 (nothing to key the entry on). */
    public synchronized int mutate(UUID attacker, UUID victim, String enchantId, String key,
                                    TransientStateDefinition def, String op, int amount) {
        if (attacker == null) return 0;
        Entry entry = entries.computeIfAbsent(new TrackerKey(attacker, enchantId, key), k -> new Entry());

        if (wouldReset(entry, victim, def)) {
            entry.count = 0;
        }

        int current = entry.count;
        int updated = switch (op == null ? "" : op.toLowerCase(Locale.ROOT)) {
            case "increment" -> current + 1;
            case "add" -> current + amount;
            case "set" -> amount;
            case "reset" -> 0;
            default -> current;
        };
        int max = def != null ? def.maxStacks() : 0;
        if (max > 0) updated = Math.min(updated, max);
        updated = Math.max(0, updated);

        entry.count = updated;
        entry.victim = victim;
        entry.lastMutateMillis = System.currentTimeMillis();
        return updated;
    }

    /** Read-only peek — applies the same reset rules as {@link #mutate} would (so a stale/expired
     *  counter reads as 0) but never writes the reset back; the next real {@link #mutate} call
     *  performs the actual reset+write. Used to back {@code $enchant.state.<key>$} reads. */
    public synchronized int peek(UUID attacker, UUID victim, String enchantId, String key, TransientStateDefinition def) {
        if (attacker == null) return 0;
        Entry entry = entries.get(new TrackerKey(attacker, enchantId, key));
        if (entry == null) return 0;
        return wouldReset(entry, victim, def) ? 0 : entry.count;
    }

    private boolean wouldReset(Entry entry, UUID victim, TransientStateDefinition def) {
        if (def == null) return false;
        if (def.resetOnTargetSwitch() && entry.victim != null && victim != null && !entry.victim.equals(victim)) {
            return true;
        }
        if (def.resetAfterSeconds() > 0 && entry.lastMutateMillis > 0) {
            long elapsedMillis = System.currentTimeMillis() - entry.lastMutateMillis;
            if (elapsedMillis > def.resetAfterSeconds() * 1000L) return true;
        }
        return false;
    }

    /** Periodic sweep, scheduled by {@code EnchantModule}. See class doc — this is the fix for the
     *  pre-overhaul unbounded memory leak. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> now - e.getValue().lastMutateMillis > CLEANUP_IDLE_MILLIS);
    }

    /** Drops every tracked entry — called from {@code EnchantModule.onDisable()} since this state
     *  is purely in-memory combat bookkeeping, not meant to survive a reload. */
    public void clear() {
        entries.clear();
    }

    /** @return number of currently-tracked entries — exposed for tests only. */
    int size() {
        return entries.size();
    }
}
