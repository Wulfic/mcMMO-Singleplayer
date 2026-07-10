package com.gmail.nossr50.platform.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.CancellableRunnable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the MC-free {@link TickScheduler} core (Phase 11). Drives {@link
 * TickScheduler#tick()} manually to assert the exact firing schedule, both cancellation paths,
 * re-entrant scheduling, and per-task exception isolation.
 */
class TickSchedulerTest {

    private TickScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TickScheduler();
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            scheduler.tick();
        }
    }

    @Test
    @DisplayName("runLater fires exactly on the Nth tick and only once")
    void runLaterFiresOnNthTick() {
        final AtomicInteger runs = new AtomicInteger();
        scheduler.runLater(runs::incrementAndGet, 3);

        scheduler.tick();
        assertEquals(0, runs.get(), "must not fire before its delay");
        scheduler.tick();
        assertEquals(0, runs.get());
        scheduler.tick();
        assertEquals(1, runs.get(), "fires on the 3rd tick");

        tick(5);
        assertEquals(1, runs.get(), "one-shot never fires again");
        assertEquals(0, scheduler.activeCount(), "one-shot is dropped after firing");
    }

    @Test
    @DisplayName("runNextTick fires on the very next tick")
    void runNextTickFiresImmediately() {
        final AtomicInteger runs = new AtomicInteger();
        scheduler.runNextTick(runs::incrementAndGet);

        scheduler.tick();
        assertEquals(1, runs.get());
    }

    @Test
    @DisplayName("runTimer fires at delay then every period")
    void runTimerRepeats() {
        final AtomicInteger runs = new AtomicInteger();
        scheduler.runTimer(runs::incrementAndGet, 2, 3);

        scheduler.tick(); // 1
        assertEquals(0, runs.get());
        scheduler.tick(); // 2 -> first fire
        assertEquals(1, runs.get());
        tick(2); // 3,4
        assertEquals(1, runs.get());
        scheduler.tick(); // 5 -> second fire
        assertEquals(2, runs.get());
        tick(3); // 6,7,8 -> third fire on 8
        assertEquals(3, runs.get());
    }

    @Test
    @DisplayName("runTimer rejects a non-positive period")
    void runTimerRejectsBadPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.runTimer(() -> {}, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.runTimer(() -> {}, 1, -5));
    }

    @Test
    @DisplayName("cancelling the handle stops a pending one-shot")
    void handleCancelStopsOneShot() {
        final AtomicInteger runs = new AtomicInteger();
        final ScheduledTask handle = scheduler.runLater(runs::incrementAndGet, 5);

        tick(2);
        handle.cancel();
        assertTrue(handle.isCancelled());
        tick(10);
        assertEquals(0, runs.get(), "cancelled one-shot never runs");
        assertEquals(0, scheduler.activeCount());
    }

    @Test
    @DisplayName("cancelling the handle stops a repeating timer")
    void handleCancelStopsTimer() {
        final AtomicInteger runs = new AtomicInteger();
        final ScheduledTask handle = scheduler.runTimer(runs::incrementAndGet, 1, 1);

        tick(3);
        final int firedSoFar = runs.get();
        assertTrue(firedSoFar >= 2);
        handle.cancel();
        tick(10);
        assertEquals(firedSoFar, runs.get(), "no further fires after cancel");
    }

    @Test
    @DisplayName("a self-cancelling CancellableRunnable stops itself")
    void selfCancellingTaskStops() {
        final AtomicInteger runs = new AtomicInteger();
        final CancellableRunnable task = new CancellableRunnable() {
            @Override
            public void run() {
                if (runs.incrementAndGet() >= 2) {
                    cancel();
                }
            }
        };
        scheduler.runTimer(task, 1, 1);

        tick(10);
        assertEquals(2, runs.get(), "task ran twice then cancelled itself");
        assertTrue(task.isCancelled());
        assertEquals(0, scheduler.activeCount());
    }

    @Test
    @DisplayName("cancelAll clears every scheduled task")
    void cancelAllClears() {
        final AtomicInteger runs = new AtomicInteger();
        scheduler.runLater(runs::incrementAndGet, 1);
        scheduler.runTimer(runs::incrementAndGet, 1, 1);
        scheduler.runNextTick(runs::incrementAndGet);
        assertEquals(3, scheduler.activeCount());

        scheduler.cancelAll();
        assertEquals(0, scheduler.activeCount());
        tick(10);
        assertEquals(0, runs.get());
    }

    @Test
    @DisplayName("a task scheduled from inside a running task runs on the next tick, not the same one")
    void reentrantSchedulingDefersToNextTick() {
        final AtomicInteger inner = new AtomicInteger();
        scheduler.runNextTick(() -> scheduler.runNextTick(inner::incrementAndGet));

        scheduler.tick(); // outer runs, schedules inner (deferred)
        assertEquals(0, inner.get(), "inner does not run in the same pump");
        assertEquals(1, scheduler.activeCount(), "inner is now queued");
        scheduler.tick();
        assertEquals(1, inner.get(), "inner runs on the following tick");
    }

    @Test
    @DisplayName("a throwing task is logged, cancelled, and does not repeat or break the pump")
    void throwingTaskIsIsolatedAndCancelled() {
        final AtomicInteger runs = new AtomicInteger();
        final AtomicInteger sibling = new AtomicInteger();
        scheduler.runTimer(() -> {
            runs.incrementAndGet();
            throw new RuntimeException("boom");
        }, 1, 1);
        scheduler.runTimer(sibling::incrementAndGet, 1, 1);

        tick(5);
        assertEquals(1, runs.get(), "throwing timer fires once then is cancelled");
        assertEquals(5, sibling.get(), "sibling task keeps ticking despite the exception");
    }
}
