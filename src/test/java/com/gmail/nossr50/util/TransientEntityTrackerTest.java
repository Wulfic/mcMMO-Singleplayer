package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the MC-free {@link TransientEntityTracker}: per-type cap counting (by
 * {@link TrackedSummon#isValid()}), the entity-id anti-farm index, and cleanup. Uses a
 * {@link FakeSummon} in place of the MC-typed {@code CotwSummon}.
 */
class TransientEntityTrackerTest {

    private TransientEntityTracker tracker;
    private UUID player;

    @BeforeEach
    void setUp() {
        tracker = new TransientEntityTracker();
        player = UUID.randomUUID();
    }

    @Test
    void countsOnlyLiveSummonsOfTheGivenType() {
        tracker.addSummon(player, new FakeSummon(CallOfTheWildType.WOLF, true));
        tracker.addSummon(player, new FakeSummon(CallOfTheWildType.WOLF, true));
        tracker.addSummon(player, new FakeSummon(CallOfTheWildType.WOLF, false)); // died: not counted
        tracker.addSummon(player, new FakeSummon(CallOfTheWildType.CAT, true));   // wrong type

        assertEquals(2, tracker.countActiveOfType(player, CallOfTheWildType.WOLF));
        assertEquals(1, tracker.countActiveOfType(player, CallOfTheWildType.CAT));
        assertEquals(0, tracker.countActiveOfType(player, CallOfTheWildType.HORSE));
    }

    @Test
    void countIsPerPlayer() {
        final UUID other = UUID.randomUUID();
        tracker.addSummon(player, new FakeSummon(CallOfTheWildType.WOLF, true));

        assertEquals(1, tracker.countActiveOfType(player, CallOfTheWildType.WOLF));
        assertEquals(0, tracker.countActiveOfType(other, CallOfTheWildType.WOLF));
    }

    @Test
    void isTransientTracksSummonedEntityIds() {
        final FakeSummon summon = new FakeSummon(CallOfTheWildType.WOLF, true);
        tracker.addSummon(player, summon);

        assertTrue(tracker.isTransient(summon.getEntityId()));
        assertFalse(tracker.isTransient(UUID.randomUUID()));
    }

    @Test
    void evictByEntityIdDropsItFromCountAndIndex() {
        final FakeSummon summon = new FakeSummon(CallOfTheWildType.WOLF, true);
        tracker.addSummon(player, summon);

        tracker.evictByEntityId(summon.getEntityId());

        assertFalse(tracker.isTransient(summon.getEntityId()));
        assertEquals(0, tracker.countActiveOfType(player, CallOfTheWildType.WOLF));
        assertFalse(summon.despawned, "evict must not despawn the (already-gone) entity");
    }

    @Test
    void cleanupPlayerDespawnsEverySummonAndForgetsThem() {
        final FakeSummon wolf = new FakeSummon(CallOfTheWildType.WOLF, true);
        final FakeSummon horse = new FakeSummon(CallOfTheWildType.HORSE, true);
        tracker.addSummon(player, wolf);
        tracker.addSummon(player, horse);

        tracker.cleanupPlayer(player);

        assertTrue(wolf.despawned);
        assertTrue(horse.despawned);
        assertFalse(wolf.timeExpired, "logout cleanup despawns with timeExpired=false");
        assertFalse(tracker.isTransient(wolf.getEntityId()));
        assertEquals(0, tracker.countActiveOfType(player, CallOfTheWildType.WOLF));
    }

    @Test
    void removeSummonDropsASingleSummon() {
        final FakeSummon a = new FakeSummon(CallOfTheWildType.WOLF, true);
        final FakeSummon b = new FakeSummon(CallOfTheWildType.WOLF, true);
        tracker.addSummon(player, a);
        tracker.addSummon(player, b);

        tracker.removeSummon(player, a);

        assertFalse(tracker.isTransient(a.getEntityId()));
        assertTrue(tracker.isTransient(b.getEntityId()));
        assertEquals(1, tracker.countActiveOfType(player, CallOfTheWildType.WOLF));
    }

    /** A server-free stand-in for {@code CotwSummon}. */
    private static final class FakeSummon implements TrackedSummon {
        private final CallOfTheWildType type;
        private final UUID entityId = UUID.randomUUID();
        private final boolean valid;
        private boolean despawned = false;
        private boolean timeExpired = false;

        FakeSummon(CallOfTheWildType type, boolean valid) {
            this.type = type;
            this.valid = valid;
        }

        @Override
        public CallOfTheWildType getCallOfTheWildType() {
            return type;
        }

        @Override
        public UUID getEntityId() {
            return entityId;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void despawn(boolean timeExpired) {
            this.despawned = true;
            this.timeExpired = timeExpired;
        }
    }
}
