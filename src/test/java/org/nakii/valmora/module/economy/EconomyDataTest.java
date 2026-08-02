package org.nakii.valmora.module.economy;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link EconomyData}'s compound operations stay correct under real concurrent
 * access — the property the write-behind economy backend relies on to be safe at scale
 * without a global lock.
 */
class EconomyDataTest {

    @Test
    void addPurseNeverLosesUpdatesUnderConcurrentAccess() throws InterruptedException {
        EconomyData data = new EconomyData(0, 0);
        int threads = 16;
        int incrementsPerThread = 5_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < incrementsPerThread; i++) {
                        data.addPurse(1.0);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "worker threads did not finish in time");
        pool.shutdown();

        assertEquals(threads * incrementsPerThread, data.getPurse(), 1e-6);
    }

    @Test
    void removePurseClampsAtZeroAndNeverGoesNegative() throws InterruptedException {
        EconomyData data = new EconomyData(1000, 0);
        int threads = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 200; i++) data.removePurse(1.0);
                done.countDown();
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(0.0, data.getPurse(), 1e-6);
    }

    @Test
    void depositAllMovesEntirePurseAtomically() {
        EconomyData data = new EconomyData(500, 100);
        double moved = data.depositAll();
        assertEquals(500.0, moved, 1e-6);
        assertEquals(0.0, data.getPurse(), 1e-6);
        assertEquals(600.0, data.getBank(), 1e-6);

        // Calling again on an empty purse must be a true no-op.
        assertEquals(0.0, data.depositAll(), 1e-6);
    }

    @Test
    void depositFailsWithoutMutatingWhenPurseIsShort() {
        EconomyData data = new EconomyData(10, 0);
        assertFalse(data.deposit(50));
        assertEquals(10.0, data.getPurse(), 1e-6);
        assertEquals(0.0, data.getBank(), 1e-6);
    }

    @Test
    void withdrawFailsWithoutMutatingWhenBankIsShort() {
        EconomyData data = new EconomyData(0, 10);
        assertFalse(data.withdraw(50));
        assertEquals(0.0, data.getPurse(), 1e-6);
        assertEquals(10.0, data.getBank(), 1e-6);
    }
}
