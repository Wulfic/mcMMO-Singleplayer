package com.gmail.nossr50.event;

import java.util.function.Consumer;

/**
 * Internal publish/subscribe bus for mcMMO's own {@link Event}s.
 *
 * <p>This is the Fabric-port replacement for Bukkit's event system as it was used
 * <em>internally</em> by mcMMO (see {@code SelfListener}, {@code SkillManager}s that fire
 * {@code McMMOPlayerXpGainEvent}, etc.). It is not a general modding API — it only carries
 * mcMMO's own events between mcMMO's own components.
 *
 * <p>Dispatch semantics deliberately match Bukkit so listener code ports mechanically:
 * <ul>
 *   <li><b>Hierarchy:</b> a handler subscribed to a type receives that type and every
 *       subtype — a handler on {@code McMMOPlayerExperienceEvent} also sees
 *       {@code McMMOPlayerXpGainEvent}.</li>
 *   <li><b>Priority:</b> handlers run {@link EventPriority#LOWEST} → {@link EventPriority#MONITOR};
 *       within one priority, in registration order.</li>
 *   <li><b>Cancellation:</b> if the event is {@link Cancellable}, handlers registered with
 *       {@code ignoreCancelled = true} are skipped once it has been cancelled.</li>
 *   <li><b>Mutation:</b> handlers receive the live event instance and may mutate it; later
 *       handlers observe the changes.</li>
 * </ul>
 */
public interface EventBus {

    /**
     * Subscribe a handler at {@link EventPriority#NORMAL} that runs even if the event has
     * been cancelled.
     */
    default <E extends Event> void subscribe(Class<E> type, Consumer<E> handler) {
        subscribe(type, EventPriority.NORMAL, false, handler);
    }

    /**
     * Subscribe a handler to {@code type} and all its subtypes.
     *
     * @param type            event class to listen for (subtypes included)
     * @param priority        invocation order relative to other handlers of the same event
     * @param ignoreCancelled if {@code true}, skip this handler when the event is already
     *                        cancelled (only meaningful for {@link Cancellable} events)
     * @param handler         callback invoked with the live event instance
     */
    <E extends Event> void subscribe(Class<E> type, EventPriority priority,
            boolean ignoreCancelled, Consumer<E> handler);

    /**
     * Dispatch {@code event} to every matching handler, in priority order, and return the
     * same instance so callers can inspect the settled state (e.g. cancellation, mutated XP).
     *
     * @return the {@code event} argument, after all handlers have run
     */
    <E extends Event> E post(E event);
}
