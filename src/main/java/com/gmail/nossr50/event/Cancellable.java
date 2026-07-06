package com.gmail.nossr50.event;

/**
 * Marks an {@link Event} whose default action can be prevented by a handler.
 *
 * <p>Deliberately mirrors {@code org.bukkit.event.Cancellable} (same method names) so the
 * legacy events and the code that reads them ({@code event.setCancelled(true)},
 * {@code event.isCancelled()}) port mechanically. When {@link EventBus#post} is given a
 * cancellable event, handlers registered with {@code ignoreCancelled = true} are skipped
 * once the event has been cancelled — see {@link EventBus}.
 */
public interface Cancellable {

    /** @return {@code true} if the event's default action has been cancelled by a handler. */
    boolean isCancelled();

    /** @param cancelled {@code true} to prevent the event's default action. */
    void setCancelled(boolean cancelled);
}
