package com.gmail.nossr50.event;

/**
 * Base type for mcMMO's own internal events.
 *
 * <p>Replaces {@code org.bukkit.event.Event}. In the original plugin mcMMO fired these
 * through Bukkit's {@code HandlerList} machinery both for its own listeners (see
 * {@code SelfListener}) and as a public API surface for third-party plugins. In a
 * singleplayer Fabric mod there is no external plugin API to preserve, so the whole
 * {@code HandlerList}/registration apparatus collapses into the lightweight
 * {@link EventBus} — this base carries no Bukkit state at all.
 *
 * <p>Concrete event classes under the legacy {@code events/} package are ported onto this
 * base alongside the skills that fire them (Phase 10): drop {@code extends
 * org.bukkit.event.Event}, the {@code HandlerList} field and the {@code getHandlers()} /
 * {@code getHandlerList()} methods, and extend this instead. Cancellable events additionally
 * implement {@link Cancellable}.
 */
public abstract class Event {

    /**
     * @return the simple class name of this event, used only for logging/debugging. Mirrors
     *         the shape of Bukkit's {@code getEventName()} so ported debug code keeps working.
     */
    public String getEventName() {
        return getClass().getSimpleName();
    }
}
