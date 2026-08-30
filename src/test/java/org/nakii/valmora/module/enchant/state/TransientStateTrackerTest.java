package org.nakii.valmora.module.enchant.state;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Directly regression-tests the bug fix this class exists for: the pre-overhaul
 * {@code LethalityLogic} kept one stack counter keyed only by victim UUID, so two different
 * attackers hitting the same mob shared (and polluted) each other's stacks.
 */
class TransientStateTrackerTest {

    private final TransientStateDefinition plain = new TransientStateDefinition(0, false, 0);

    @Test
    void twoDifferentAttackersOnTheSameVictimGetIndependentStacks() {
        TransientStateTracker tracker = new TransientStateTracker();
        UUID attackerA = UUID.randomUUID();
        UUID attackerB = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        assertEquals(1, tracker.mutate(attackerA, victim, "lethality", "stacks", plain, "increment", 0));
        assertEquals(2, tracker.mutate(attackerA, victim, "lethality", "stacks", plain, "increment", 0));
        // Attacker B hitting the same victim must not see (or add to) attacker A's stacks.
        assertEquals(1, tracker.mutate(attackerB, victim, "lethality", "stacks", plain, "increment", 0));
        assertEquals(3, tracker.mutate(attackerA, victim, "lethality", "stacks", plain, "increment", 0));
    }

    @Test
    void incrementAddSetResetOperationsBehaveAsExpected() {
        TransientStateTracker tracker = new TransientStateTracker();
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        assertEquals(1, tracker.mutate(attacker, victim, "e", "k", plain, "increment", 0));
        assertEquals(6, tracker.mutate(attacker, victim, "e", "k", plain, "add", 5));
        assertEquals(10, tracker.mutate(attacker, victim, "e", "k", plain, "set", 10));
        assertEquals(0, tracker.mutate(attacker, victim, "e", "k", plain, "reset", 0));
    }

    @Test
    void maxStacksClampsTheCount() {
        TransientStateTracker tracker = new TransientStateTracker();
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        var capped = new TransientStateDefinition(0, false, 3);

        for (int i = 0; i < 5; i++) {
            tracker.mutate(attacker, victim, "e", "k", capped, "increment", 0);
        }
        assertEquals(3, tracker.peek(attacker, victim, "e", "k", capped));
    }

    @Test
    void resetOnTargetSwitchZeroesTheCounterWhenVictimChanges() {
        TransientStateTracker tracker = new TransientStateTracker();
        UUID attacker = UUID.randomUUID();
        UUID victimA = UUID.randomUUID();
        UUID victimB = UUID.randomUUID();
        var switching = new TransientStateDefinition(0, true, 0);

        tracker.mutate(attacker, victimA, "e", "k", switching, "increment", 0);
        tracker.mutate(attacker, victimA, "e", "k", switching, "increment", 0);
        assertEquals(2, tracker.peek(attacker, victimA, "e", "k", switching));

        // Switching to a different victim resets the count back to 0 before the next hit lands.
        assertEquals(1, tracker.mutate(attacker, victimB, "e", "k", switching, "increment", 0));
    }

    @Test
    void resetAfterSecondsDoesNotExpireAFreshCounter() {
        TransientStateTracker tracker = new TransientStateTracker();
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        // No clock seam to simulate real elapsed time in a unit test — this just confirms a
        // just-written counter with a reset window configured isn't immediately treated as stale.
        var expiring = new TransientStateDefinition(30, false, 0);
        tracker.mutate(attacker, victim, "e", "k", expiring, "increment", 0);
        assertEquals(1, tracker.peek(attacker, victim, "e", "k", expiring));
    }

    @Test
    void unknownAttackerOrVictimIsANoOp() {
        TransientStateTracker tracker = new TransientStateTracker();
        assertEquals(0, tracker.mutate(null, UUID.randomUUID(), "e", "k", plain, "increment", 0));
        assertEquals(0, tracker.peek(null, UUID.randomUUID(), "e", "k", plain));
        assertEquals(0, tracker.peek(UUID.randomUUID(), UUID.randomUUID(), "e", "k", plain));
    }

    @Test
    void cleanupPurgesEntriesOnlyPastTheIdleWindow() {
        TransientStateTracker tracker = new TransientStateTracker();
        tracker.mutate(UUID.randomUUID(), UUID.randomUUID(), "e", "k", plain, "increment", 0);
        assertEquals(1, tracker.size());

        // A fresh entry is well within the 15-minute idle window, so cleanup must not touch it.
        tracker.cleanup();
        assertEquals(1, tracker.size());
    }

    @Test
    void clearDropsEveryEntry() {
        TransientStateTracker tracker = new TransientStateTracker();
        tracker.mutate(UUID.randomUUID(), UUID.randomUUID(), "e", "k", plain, "increment", 0);
        tracker.clear();
        assertEquals(0, tracker.size());
    }
}
