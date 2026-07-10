package com.gmail.nossr50.util;

/**
 * Base class for mcMMO's scheduled tasks (Phase 11).
 *
 * <p>Legacy mcMMO extended a FoliaLib-coupled {@code CancellableRunnable} that implemented
 * {@code Consumer<WrappedTask>}; this Fabric port drops the FoliaLib dependency and is a plain
 * {@link Runnable} so it can be handed straight to
 * {@link com.gmail.nossr50.platform.scheduler.TaskScheduler}. The self-cancel idiom is preserved:
 * a task calls {@link #cancel()} from inside {@link #run()} to stop a repeating task, and the
 * scheduler drops it after the current tick (see
 * {@link com.gmail.nossr50.platform.scheduler.TickScheduler}).
 */
public abstract class CancellableRunnable implements Runnable {

    private boolean cancelled = false;

    /** Requests that the scheduler stop running this task (after the current run, if any). */
    public void cancel() {
        cancelled = true;
    }

    /** Whether {@link #cancel()} has been called. */
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public abstract void run();
}
