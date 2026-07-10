package com.gmail.nossr50.platform.scheduler;

import com.gmail.nossr50.util.CancellableRunnable;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MC-free {@link TaskScheduler} driven by a manual {@link #tick()} pump (Phase 11).
 *
 * <p>Holds no Minecraft types so the scheduling/cancellation/re-arm logic is fully unit-testable;
 * the Fabric entry point ({@code McMMOMod}) registers {@code ServerTickEvents.END_SERVER_TICK} to
 * call {@link #tick()} once per server tick. This is the keystone that replaces FoliaLib for every
 * ported {@code runnables/} task.
 *
 * <p>Timing model: an entry's {@code remaining} counter is the number of ticks left before it
 * fires. Each {@link #tick()} pre-decrements it and fires when it reaches {@code 0}, so a task
 * scheduled with {@code delayTicks = N} runs on the N-th tick after scheduling (and {@code N <= 0}
 * runs on the very next tick). A repeating task re-arms {@code remaining = period} after each run.
 *
 * <p>Not thread-safe: all access must happen on the server thread (schedule from listeners/tasks,
 * pump from the tick event). Tasks scheduled from inside a running task are deferred to the next
 * tick (never run twice in the same pump), matching Bukkit's behaviour.
 */
public final class TickScheduler implements TaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO-Scheduler");

    /** A single scheduled unit of work plus its cancellation handle. */
    private final class Entry implements ScheduledTask {
        private final Runnable task;
        /** Ticks between runs; {@code <= 0} marks a one-shot task. */
        private final long period;
        /** Ticks left before the next run. */
        private long remaining;
        private boolean cancelled;

        Entry(@NotNull Runnable task, long delay, long period) {
            this.task = task;
            this.period = period;
            this.remaining = delay;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            // Honour both cancellation paths: this handle, and a self-cancelling runnable that
            // called cancel() on itself from inside run() (the legacy CancellableRunnable idiom).
            return cancelled
                    || (task instanceof CancellableRunnable cr && cr.isCancelled());
        }
    }

    private final List<Entry> active = new ArrayList<>();
    /** Tasks scheduled while {@link #tick()} is running; merged in after the pump completes. */
    private final List<Entry> pending = new ArrayList<>();
    private boolean ticking;

    @Override
    public @NotNull ScheduledTask runLater(@NotNull Runnable task, long delayTicks) {
        return add(task, delayTicks, 0);
    }

    @Override
    public @NotNull ScheduledTask runTimer(@NotNull Runnable task, long delayTicks,
            long periodTicks) {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks must be > 0, got " + periodTicks);
        }
        return add(task, delayTicks, periodTicks);
    }

    @Override
    public void cancelAll() {
        // Not valid mid-tick (would CME the pump); only ever called at server stop.
        active.clear();
        pending.clear();
    }

    private @NotNull ScheduledTask add(@NotNull Runnable task, long delay, long period) {
        final Entry entry = new Entry(task, delay, period);
        (ticking ? pending : active).add(entry);
        return entry;
    }

    /**
     * Advances the scheduler by one server tick: fires every due task, re-arms surviving timers,
     * and drops finished or cancelled tasks. A task that throws is logged and cancelled so a broken
     * timer cannot spam every tick.
     */
    public void tick() {
        ticking = true;
        try {
            final var it = active.iterator();
            while (it.hasNext()) {
                final Entry entry = it.next();
                if (entry.isCancelled()) {
                    it.remove();
                    continue;
                }
                if (--entry.remaining > 0) {
                    continue; // not due yet
                }
                runEntry(entry);
                if (entry.isCancelled() || entry.period <= 0) {
                    it.remove(); // one-shot, or cancelled itself during the run
                } else {
                    entry.remaining = entry.period; // re-arm the timer
                }
            }
        } finally {
            ticking = false;
            if (!pending.isEmpty()) {
                active.addAll(pending);
                pending.clear();
            }
        }
    }

    private void runEntry(@NotNull Entry entry) {
        try {
            entry.task.run();
        } catch (Throwable t) {
            LOGGER.error("Scheduled task {} threw; cancelling it.",
                    entry.task.getClass().getName(), t);
            entry.cancel();
        }
    }

    /** Number of live (scheduled but not yet finished) tasks. Exposed for tests/diagnostics. */
    public int activeCount() {
        return active.size() + pending.size();
    }
}
