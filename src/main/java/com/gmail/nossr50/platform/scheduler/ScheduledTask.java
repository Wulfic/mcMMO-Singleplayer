package com.gmail.nossr50.platform.scheduler;

/**
 * A handle to a task submitted to the {@link TaskScheduler} (Phase 11).
 *
 * <p>The Fabric replacement for FoliaLib's {@code WrappedTask}. Callers that need to stop a
 * repeating task early (or a delayed task before it fires) keep the handle returned by the
 * {@code runX} methods and call {@link #cancel()}.
 */
public interface ScheduledTask {

    /**
     * Cancels this task. A one-shot task that has not yet run will never run; a repeating task
     * stops after the current tick. Idempotent.
     */
    void cancel();

    /** Whether this task has been cancelled (via this handle or a self-cancelling runnable). */
    boolean isCancelled();
}
