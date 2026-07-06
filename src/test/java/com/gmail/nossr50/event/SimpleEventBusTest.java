package com.gmail.nossr50.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SimpleEventBus}. This is intentionally the first piece of the port
 * with a real test suite: the event bus is pure Java with no {@code net.minecraft} coupling,
 * so it runs without an in-game harness (unlike the registry-backed {@code platform/}
 * adapters, which wait for Phase 12). The behaviours pinned here mirror the Bukkit dispatch
 * semantics that mcMMO's ported listeners will depend on.
 */
class SimpleEventBusTest {

    // --- test event hierarchy, mirroring the real events/experience/* shape ----------------

    static class ExperienceEvent extends Event {
    }

    static class XpGainEvent extends ExperienceEvent implements Cancellable {
        float rawXp;
        boolean cancelled;

        XpGainEvent(float rawXp) {
            this.rawXp = rawXp;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    private SimpleEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new SimpleEventBus();
    }

    @Test
    void postReturnsSameInstanceAndReachesHandler() {
        List<XpGainEvent> seen = new ArrayList<>();
        bus.subscribe(XpGainEvent.class, seen::add);

        XpGainEvent event = new XpGainEvent(10f);
        XpGainEvent returned = bus.post(event);

        assertSame(event, returned, "post must return the same event instance");
        assertEquals(List.of(event), seen);
    }

    @Test
    void handlersRunInPriorityThenRegistrationOrder() {
        List<String> order = new ArrayList<>();
        // Register out of priority order, and two at the same priority, to prove both keys.
        bus.subscribe(XpGainEvent.class, EventPriority.MONITOR, false, e -> order.add("monitor"));
        bus.subscribe(XpGainEvent.class, EventPriority.NORMAL, false, e -> order.add("normal-a"));
        bus.subscribe(XpGainEvent.class, EventPriority.LOWEST, false, e -> order.add("lowest"));
        bus.subscribe(XpGainEvent.class, EventPriority.NORMAL, false, e -> order.add("normal-b"));

        bus.post(new XpGainEvent(1f));

        assertEquals(List.of("lowest", "normal-a", "normal-b", "monitor"), order);
    }

    @Test
    void handlerOnSupertypeReceivesSubtypeEvents() {
        List<Event> seen = new ArrayList<>();
        bus.subscribe(ExperienceEvent.class, seen::add);

        XpGainEvent event = new XpGainEvent(5f);
        bus.post(event);

        assertEquals(List.of(event), seen, "supertype handler must see subtype events");
    }

    @Test
    void unrelatedHandlerDoesNotReceiveEvent() {
        List<Event> seen = new ArrayList<>();
        bus.subscribe(XpGainEvent.class, seen::add);

        bus.post(new ExperienceEvent()); // a supertype instance is NOT an XpGainEvent

        assertTrue(seen.isEmpty(), "subtype handler must not see supertype-only events");
    }

    @Test
    void ignoreCancelledHandlerIsSkippedOnceCancelled() {
        List<String> order = new ArrayList<>();
        bus.subscribe(XpGainEvent.class, EventPriority.LOW, false, e -> {
            order.add("canceller");
            e.setCancelled(true);
        });
        bus.subscribe(XpGainEvent.class, EventPriority.NORMAL, true, e -> order.add("skipped"));
        bus.subscribe(XpGainEvent.class, EventPriority.HIGH, false, e -> order.add("still-runs"));

        bus.post(new XpGainEvent(1f));

        assertEquals(List.of("canceller", "still-runs"), order);
    }

    @Test
    void handlersObserveMutationsFromEarlierHandlers() {
        bus.subscribe(XpGainEvent.class, EventPriority.NORMAL, false, e -> e.rawXp *= 2);
        float[] observedAtMonitor = {Float.NaN};
        bus.subscribe(XpGainEvent.class, EventPriority.MONITOR, false,
                e -> observedAtMonitor[0] = e.rawXp);

        XpGainEvent event = bus.post(new XpGainEvent(10f));

        assertEquals(20f, event.rawXp);
        assertEquals(20f, observedAtMonitor[0], "MONITOR handler must see the settled value");
    }

    @Test
    void throwingHandlerIsIsolatedAndLaterHandlersStillRun() {
        List<String> order = new ArrayList<>();
        bus.subscribe(XpGainEvent.class, EventPriority.LOW, false, e -> order.add("before"));
        bus.subscribe(XpGainEvent.class, EventPriority.NORMAL, false, e -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(XpGainEvent.class, EventPriority.HIGH, false, e -> order.add("after"));

        bus.post(new XpGainEvent(1f)); // must not propagate

        assertEquals(List.of("before", "after"), order);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.subscribe(null, EventPriority.NORMAL, false, e -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> bus.subscribe(XpGainEvent.class, EventPriority.NORMAL, false, null));
        assertThrows(IllegalArgumentException.class, () -> bus.post(null));
    }

    @Test
    void handlerCountReflectsSubscriptions() {
        assertEquals(0, bus.handlerCount());
        bus.subscribe(XpGainEvent.class, e -> { });
        bus.subscribe(ExperienceEvent.class, e -> { });
        assertEquals(2, bus.handlerCount());
    }

    @Test
    void nonCancellableEventIsUnaffectedByIgnoreCancelledFlag() {
        List<String> order = new ArrayList<>();
        // ignoreCancelled on a non-Cancellable event must be a no-op (handler always runs).
        bus.subscribe(ExperienceEvent.class, EventPriority.NORMAL, true, e -> order.add("ran"));

        bus.post(new ExperienceEvent());

        assertEquals(List.of("ran"), order);
        assertFalse(order.isEmpty());
    }
}
