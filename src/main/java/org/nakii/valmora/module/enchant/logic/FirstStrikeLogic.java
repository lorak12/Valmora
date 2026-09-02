package org.nakii.valmora.module.enchant.logic;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.combat.DamageModifierContext;
import org.nakii.valmora.module.enchant.EnchantmentLogic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "First Strike" (`valmora:first_strike`) — a damage bonus per level on the first
 * {@link #MAX_HITS} hits landed on a given target, per attacker/target pair. The hit count
 * resets after {@link #RESET_WINDOW_MS} of no hits on that target (a fresh "engagement"), rather
 * than tracking target death directly — simpler and avoids a separate death-listener wire-up for
 * a purely cosmetic reset boundary. Added 2026-08-07 to wire up one of the 7 previously-inert
 * shipped enchants.
 */
public class FirstStrikeLogic implements EnchantmentLogic {

    private static final int DEFAULT_MAX_HITS = 3;
    private static final long DEFAULT_RESET_WINDOW_MS = 10_000;

    private final double percentPerLevel;
    // HC-115: per-enchant window balance, overridable via `logic-params: {max-hits, reset-window-ms}`.
    private final int maxHits;
    private final long resetWindowMs;
    // victim uuid -> attacker uuid -> [hitCount, lastHitMillis]. A plain long[2] mutable cell
    // avoids a small record/class just for this internal bookkeeping.
    private final Map<UUID, Map<UUID, long[]>> hitState = new ConcurrentHashMap<>();

    public FirstStrikeLogic(double percentPerLevel) {
        this(percentPerLevel, DEFAULT_MAX_HITS, DEFAULT_RESET_WINDOW_MS);
    }

    public FirstStrikeLogic(double percentPerLevel, int maxHits, long resetWindowMs) {
        this.percentPerLevel = percentPerLevel;
        this.maxHits = maxHits;
        this.resetWindowMs = resetWindowMs;
    }

    @Override
    public void modifyAttack(DamageModifierContext context, LivingEntity attacker, LivingEntity victim, int level) {
        Map<UUID, long[]> perAttacker = hitState.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>());
        long[] state = perAttacker.computeIfAbsent(attacker.getUniqueId(), k -> new long[]{0, 0});

        long now = System.currentTimeMillis();
        if (now - state[1] > resetWindowMs) state[0] = 0;
        state[1] = now;

        if (state[0] < maxHits) {
            context.setDamageMultiplier(context.getDamageMultiplier() * (1.0 + (percentPerLevel / 100.0) * level));
            state[0]++;
        }
    }
}
