package org.nakii.valmora.module.item.impl;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Covers {@link ChargeJumpTracker} — the pure charge→force math backing Spring Boots' "To the
 *  Moon!" (see docs/IMPLEMENTATION_BACKLOG.md's item-mechanic-engine CHARGE_JUMP item). */
public class ChargeJumpTrackerTest {

    @Test
    void computeForceAtZeroElapsedReturnsMinForce() {
        var params = new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2);
        assertEquals(0.4, ChargeJumpTracker.computeForce(0, params), 1e-9);
    }

    @Test
    void computeForceAtFullChargeReturnsMaxForce() {
        var params = new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2);
        assertEquals(2.2, ChargeJumpTracker.computeForce(2000, params), 1e-9);
    }

    @Test
    void computeForceInterpolatesLinearlyAtHalfCharge() {
        var params = new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2);
        assertEquals(1.3, ChargeJumpTracker.computeForce(1000, params), 1e-9); // midpoint of 0.4..2.2
    }

    @Test
    void computeForceClampsPastMaxChargeDuration() {
        var params = new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2);
        assertEquals(2.2, ChargeJumpTracker.computeForce(10_000, params), 1e-9); // holding past the cap doesn't jump higher
    }

    @Test
    void releaseChargeReturnsEmptyWhenNoChargeInProgress() {
        UUID uuid = UUID.randomUUID();
        assertTrue(ChargeJumpTracker.releaseCharge(uuid).isEmpty());
    }

    @Test
    void startThenImmediateReleaseReturnsApproximatelyMinForce() {
        UUID uuid = UUID.randomUUID();
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));
        double force = ChargeJumpTracker.releaseCharge(uuid).orElseThrow();
        assertTrue(force >= 0.4 && force < 0.5, "expected near-minimum force, got " + force);
    }

    @Test
    void releaseChargeConsumesTheChargeEntry() {
        UUID uuid = UUID.randomUUID();
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));
        assertTrue(ChargeJumpTracker.releaseCharge(uuid).isPresent());
        assertTrue(ChargeJumpTracker.releaseCharge(uuid).isEmpty()); // consumed — 2nd release is empty
    }

    @Test
    void isChargingReflectsActiveState() {
        UUID uuid = UUID.randomUUID();
        assertFalse(ChargeJumpTracker.isCharging(uuid));
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));
        assertTrue(ChargeJumpTracker.isCharging(uuid));
        ChargeJumpTracker.releaseCharge(uuid);
        assertFalse(ChargeJumpTracker.isCharging(uuid));
    }

    @Test
    void cancelChargeClearsWithoutReturningAForce() {
        UUID uuid = UUID.randomUUID();
        ChargeJumpTracker.startCharge(uuid, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));
        ChargeJumpTracker.cancelCharge(uuid);
        assertFalse(ChargeJumpTracker.isCharging(uuid));
        assertTrue(ChargeJumpTracker.releaseCharge(uuid).isEmpty());
    }

    @Test
    void chargesForDifferentPlayersAreIndependent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ChargeJumpTracker.startCharge(a, new ChargeJumpTracker.ChargeParams(2000, 0.4, 2.2));
        assertTrue(ChargeJumpTracker.isCharging(a));
        assertFalse(ChargeJumpTracker.isCharging(b));
        ChargeJumpTracker.cancelCharge(a);
    }
}
