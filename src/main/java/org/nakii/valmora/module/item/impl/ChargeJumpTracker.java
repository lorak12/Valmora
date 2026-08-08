package org.nakii.valmora.module.item.impl;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-progress "charge your jump by sneaking" state (Spring Boots' "To the Moon!" —
 * {@link ChargeJumpMechanic}/{@code ChargeJumpListener}). Kept separate from the mechanic/listener
 * classes so the charge→force math ({@link #computeForce}) is a pure function, testable without
 * any Bukkit mocking.
 */
public final class ChargeJumpTracker {

    private ChargeJumpTracker() {}

    /** {@code minYForce}/{@code maxYForce} are the launch velocity at 0% and 100% charge. */
    public record ChargeParams(long maxChargeMs, double minYForce, double maxYForce) {}

    private static final Map<UUID, Long> chargeStartMillis = new ConcurrentHashMap<>();
    private static final Map<UUID, ChargeParams> chargeParams = new ConcurrentHashMap<>();

    /** Begins charging — called when the SNEAK ability trigger fires (charge start). */
    public static void startCharge(UUID playerId, ChargeParams params) {
        chargeStartMillis.put(playerId, System.currentTimeMillis());
        chargeParams.put(playerId, params);
    }

    /**
     * Ends charging (un-sneak) and returns the launch y-force to apply, or empty if this player
     * had no charge in progress (e.g. un-sneaking for an unrelated reason, or no Spring Boots worn
     * — {@link ChargeJumpMechanic#execute} is the only thing that ever calls {@link #startCharge}).
     */
    public static Optional<Double> releaseCharge(UUID playerId) {
        Long start = chargeStartMillis.remove(playerId);
        ChargeParams params = chargeParams.remove(playerId);
        if (start == null || params == null) return Optional.empty();
        return Optional.of(computeForce(System.currentTimeMillis() - start, params));
    }

    /** Whether this player currently has a charge in progress. */
    public static boolean isCharging(UUID playerId) {
        return chargeStartMillis.containsKey(playerId);
    }

    /** Cancels a charge without launching — used on player quit to avoid a stale leaked entry. */
    public static void cancelCharge(UUID playerId) {
        chargeStartMillis.remove(playerId);
        chargeParams.remove(playerId);
    }

    /**
     * Linear interpolation between {@code minYForce} (0% charge) and {@code maxYForce} (100%
     * charge, i.e. held for {@code maxChargeMs} or longer — charge fraction is clamped to
     * {@code [0, 1]}, so holding past the cap doesn't jump higher).
     */
    static double computeForce(long elapsedMs, ChargeParams params) {
        double fraction = Math.max(0.0, Math.min(1.0, elapsedMs / (double) params.maxChargeMs()));
        return params.minYForce() + (params.maxYForce() - params.minYForce()) * fraction;
    }
}
