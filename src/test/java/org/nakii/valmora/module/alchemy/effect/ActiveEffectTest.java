package org.nakii.valmora.module.alchemy.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Covers {@link ActiveEffect}'s expiry/remaining-time math — previously untested (per
 *  docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "unit tests for untested modules" item,
 *  Alchemy module — `AlchemyEffectTest` already covers the definition-level `AlchemyEffect`,
 *  this covers the per-player active-instance record alongside it). */
public class ActiveEffectTest {

    @Test
    void isExpiredIsFalseWhileExpiryIsInTheFuture() {
        ActiveEffect effect = new ActiveEffect("speed", 3, System.currentTimeMillis() + 60_000);
        assertFalse(effect.isExpired());
    }

    @Test
    void isExpiredIsTrueOnceExpiryHasPassed() {
        ActiveEffect effect = new ActiveEffect("speed", 3, System.currentTimeMillis() - 1);
        assertTrue(effect.isExpired());
    }

    @Test
    void isExpiredIsTrueAtExactExpiryInstant() {
        long now = System.currentTimeMillis();
        ActiveEffect effect = new ActiveEffect("speed", 3, now);
        assertTrue(now >= effect.expiresAtMs()); // sanity: our clock read didn't regress
        assertTrue(effect.isExpired());
    }

    @Test
    void remainingSecondsRoundsDownAndNeverGoesNegative() {
        long now = System.currentTimeMillis();
        ActiveEffect farFuture = new ActiveEffect("speed", 3, now + 65_500);
        int remaining = farFuture.remainingSeconds();
        assertTrue(remaining == 65 || remaining == 64, "expected ~65s, got " + remaining);

        ActiveEffect expired = new ActiveEffect("speed", 3, now - 100_000);
        assertEquals(0, expired.remainingSeconds());
    }

    @Test
    void recordAccessorsExposeConstructorFields() {
        ActiveEffect effect = new ActiveEffect("poison", 5, 123456789L);
        assertEquals("poison", effect.effectId());
        assertEquals(5, effect.level());
        assertEquals(123456789L, effect.expiresAtMs());
    }
}
