package com.gmail.nossr50.platform.scheduler;

import org.jetbrains.annotations.NotNull;

/**
 * mcMMO's server-tick scheduling seam (Phase 11) — the Fabric replacement for the legacy
 * {@code mcMMO.p.getFoliaLib().getScheduler()} surface.
 *
 * <p>Bukkit/Folia exposed region- and thread-aware variants ({@code runAtEntityLater},
 * {@code runLaterAsync}, {@code runTimerAsync}, …). A singleplayer integrated server runs on a
 * single main thread, so every region-scoped variant collapses to a plain main-thread delay and
 * the async variants are folded onto the tick thread (mcMMO's periodic work — profile saves, DoT
 * ticks, ability cooldowns — is cheap and must observe game state coherently anyway). This
 * interface therefore keeps only the delay/period primitives the ported runnables actually need.
 *
 * <p>Delays and periods are measured in server ticks (20 ticks = 1 second). All work runs on the
 * server thread between world ticks.
 */
public interface TaskScheduler {

    /**
     * Runs {@code task} once, {@code delayTicks} server ticks from now. A {@code delayTicks} of
     * {@code 0} (or negative) runs on the next tick.
     *
     * @return a handle that can cancel the task before it fires
     */
    @NotNull ScheduledTask runLater(@NotNull Runnable task, long delayTicks);

    /**
     * Runs {@code task} repeatedly: first after {@code delayTicks}, then every {@code periodTicks}
     * thereafter, until cancelled (via the returned handle, a self-cancelling
     * {@link com.gmail.nossr50.util.CancellableRunnable}, or {@link #cancelAll()}).
     *
     * @throws IllegalArgumentException if {@code periodTicks <= 0}
     * @return a handle that can cancel the repeating task
     */
    @NotNull ScheduledTask runTimer(@NotNull Runnable task, long delayTicks, long periodTicks);

    /** Runs {@code task} once on the next server tick. Convenience for {@link #runLater}(task, 0). */
    default @NotNull ScheduledTask runNextTick(@NotNull Runnable task) {
        return runLater(task, 0);
    }

    /**
     * Cancels every scheduled task. Called at server stop so the next world session starts with an
     * empty queue. Must not be called from within a running task.
     */
    void cancelAll();
}
