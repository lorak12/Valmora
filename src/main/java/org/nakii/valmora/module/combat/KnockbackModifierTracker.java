package org.nakii.valmora.module.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VANILLA_CONTROL_AUDIT.md §14 Medium #22 — bridges {@link DamageCalculator}'s per-hit
 * {@code knockback-multiplier} (set by enchant {@code modify-attack}/{@code modify-defend} blocks)
 * to {@link CombatKnockbackListener}, which observes the separate vanilla
 * {@code EntityKnockbackEvent} the same physical hit produces a few lines later in vanilla's own
 * attack handling. There is no shared context object between the two events, so this is a short-lived
 * UUID-keyed handoff: {@code CombatListener} sets it right after {@code DamageResult#apply()} for the
 * hit it just resolved, and {@link CombatKnockbackListener} consumes (removes) it on the very next
 * {@code EntityKnockbackEvent} for that victim — a value that goes unconsumed (e.g. no knockback
 * event follows, such as a fully-blocked/absorbed hit) is simply overwritten or GC'd next hit, never
 * accumulates.
 */
final class KnockbackModifierTracker {

    private static final Map<UUID, Double> PENDING = new ConcurrentHashMap<>();

    private KnockbackModifierTracker() {}

    static void set(UUID victim, double multiplier) {
        if (multiplier == 1.0) {
            PENDING.remove(victim); // nothing to scale — avoid leaving a stale non-1.0 value around
            return;
        }
        PENDING.put(victim, multiplier);
    }

    /** Reads and removes the pending multiplier for this victim, or {@code null} if none is set. */
    static Double consume(UUID victim) {
        return PENDING.remove(victim);
    }

    static void clear(UUID victim) {
        PENDING.remove(victim);
    }
}
