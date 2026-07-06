package com.gmail.nossr50.event;

/**
 * Order in which handlers of the same event are invoked, lowest first.
 *
 * <p>Mirrors {@code org.bukkit.event.EventPriority} exactly (same names, same ordering
 * semantics) so legacy {@code @EventHandler(priority = ...)} annotations map straight onto
 * {@link EventBus} subscriptions. {@link #LOWEST} runs first and {@link #MONITOR} last;
 * {@code MONITOR} is by convention read-only (observe the final state, do not mutate).
 * mcMMO's own {@code SelfListener} relies on this: it mutates XP at {@link #NORMAL} and only
 * reads the settled value at {@link #MONITOR}.
 */
public enum EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR
}
