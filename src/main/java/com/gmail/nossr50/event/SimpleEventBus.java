package com.gmail.nossr50.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link EventBus} implementation.
 *
 * <p>Subscriptions are kept in a single list that is re-sorted on every {@link #subscribe}
 * and published as an immutable snapshot ({@link #snapshot}) that {@link #post} iterates
 * without locking. Registration is expected to happen at startup (few writes); posting
 * happens on the server thread (many reads), so a copy-on-subscribe snapshot is cheaper and
 * simpler than locking each dispatch, and it lets a handler subscribe during dispatch
 * without a {@link java.util.ConcurrentModificationException}.
 *
 * <p>A misbehaving handler must not take the whole event pipeline down: exceptions are caught
 * and logged, then the remaining handlers still run. This matches the plugin's original
 * resilience (Bukkit isolates listener exceptions) and honours the project rule that every
 * error path logs.
 */
public final class SimpleEventBus implements EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/EventBus");

    /** Stable ordering: lower priority first, then registration order (sequence). */
    private static final Comparator<Subscription<?>> ORDER =
            Comparator.<Subscription<?>>comparingInt(s -> s.priority.ordinal())
                    .thenComparingLong(s -> s.sequence);

    private final Object writeLock = new Object();
    private final List<Subscription<?>> subscriptions = new ArrayList<>();
    private long sequenceCounter = 0L;

    /** Immutable, priority-sorted view read by {@link #post} without synchronization. */
    private volatile List<Subscription<?>> snapshot = List.of();

    @Override
    public <E extends Event> void subscribe(Class<E> type, EventPriority priority,
            boolean ignoreCancelled, Consumer<E> handler) {
        if (type == null || priority == null || handler == null) {
            throw new IllegalArgumentException("type, priority and handler must not be null");
        }
        synchronized (writeLock) {
            subscriptions.add(new Subscription<>(type, priority, ignoreCancelled, handler,
                    sequenceCounter++));
            final List<Subscription<?>> sorted = new ArrayList<>(subscriptions);
            sorted.sort(ORDER);
            snapshot = List.copyOf(sorted);
        }
    }

    @Override
    public <E extends Event> E post(E event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        final boolean cancellable = event instanceof Cancellable;
        for (Subscription<?> subscription : snapshot) {
            if (!subscription.type.isInstance(event)) {
                continue;
            }
            if (cancellable && subscription.ignoreCancelled
                    && ((Cancellable) event).isCancelled()) {
                continue;
            }
            dispatch(subscription, event);
        }
        return event;
    }

    /**
     * Invoke one subscription with the event. The cast is safe because {@link #post} already
     * verified {@code subscription.type.isInstance(event)} and every subscription's handler
     * accepts its {@code type} (enforced by {@link #subscribe}'s generic signature).
     */
    @SuppressWarnings("unchecked")
    private void dispatch(Subscription<?> subscription, Event event) {
        try {
            ((Consumer<Event>) subscription.handler).accept(event);
        } catch (RuntimeException e) {
            LOGGER.error("Handler for {} threw while processing {}; continuing with remaining "
                    + "handlers", subscription.type.getSimpleName(), event.getEventName(), e);
        }
    }

    /** @return number of registered handlers; exposed for tests/diagnostics. */
    public int handlerCount() {
        return snapshot.size();
    }

    private record Subscription<E extends Event>(Class<E> type, EventPriority priority,
            boolean ignoreCancelled, Consumer<E> handler, long sequence) {
    }
}
