package com.gmail.nossr50.datatypes.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Minecraft-free coverage for Hunter's mob-origin vocabulary (D-HU1, stage 1).
 *
 * <p>Every assertion here has a live exploit or a silent regression behind it rather than restating a
 * declaration — this enum's whole job is to decide whether a permanent damage bonus may be farmed.
 */
class MobOriginTest {

    @Test
    void exactlyOneOriginCountsTowardMastery() {
        // The design property, asserted as a count rather than per-constant: the gate is
        // "unmarked mobs count, marked ones do not", so a second counting constant would mean
        // something is being written to NBT that then permits farming. Adding a legitimately
        // counting origin should therefore be a deliberate change to this number, with a reason.
        final long counting = Arrays.stream(MobOrigin.values())
                .filter(MobOrigin::countsTowardMastery)
                .count();
        assertEquals(1, counting,
                "only NATURAL may count toward mastery — any other counting constant would be "
                        + "written to a mob's NBT and then permit farming it");
        assertTrue(MobOrigin.NATURAL.countsTowardMastery());
    }

    @Test
    void everyFarmableOriginIsDisqualified() {
        // Named individually, because each one is a specific farm somebody builds.
        assertFalse(MobOrigin.SPAWNER.countsTowardMastery(),
                "a cave-spider or blaze grinder would otherwise reach the permanent +3 damage cap in "
                        + "under four hours against roughly 28 by hand");
        assertFalse(MobOrigin.BRED.countsTowardMastery(),
                "a cow pen, or shulker self-duplication, is an unbounded mastery source");
        assertFalse(MobOrigin.PLAYER_PLACED.countsTowardMastery(),
                "spawn eggs, /summon and a dispenser firing either are not hunting");
        assertFalse(MobOrigin.STRUCTURE.countsTowardMastery(),
                "portal zombified piglins arrive with SpawnReason.STRUCTURE — this is legacy's "
                        + "NETHER_PORTAL_MOB");
    }

    @Test
    void anUnreadableMarkerFailsClosed() {
        // ⚠️ THE assertion in this file. A marker exists only because something disqualified the mob,
        // so a value a future build cannot parse must stay disqualified. Flipping UNKNOWN to counting
        // would turn a single renamed constant into a silent re-opening of every farm above.
        assertFalse(MobOrigin.UNKNOWN.countsTowardMastery(),
                "an unrecognised marker must fail closed; treating it as NATURAL would silently "
                        + "re-open every farm this gate closes");
        assertNull(MobOrigin.byName("PLAYER_TAMED_MOB"),
                "an unknown stored value resolves to null so the caller can decide — here, UNKNOWN");
        assertNull(MobOrigin.byName(null));
        assertNull(MobOrigin.byName(""));
        assertNull(MobOrigin.byName("spawner"),
                "the stored key is the constant name and resolution is case-sensitive; a loose match "
                        + "would make a typo'd marker silently authoritative");
    }

    @Test
    void everyOriginRoundTripsThroughItsStorageKey() {
        // The on-disk contract. storageKey() is what lands in the mob's NBT, so a constant whose key
        // does not resolve back would read as UNKNOWN on the very next chunk load — the marker would
        // still fail closed, but every spawner mob in the world would become indistinguishable from a
        // bred one, and the §G rows that measure per-origin behaviour would all read the same.
        for (MobOrigin origin : MobOrigin.values()) {
            assertSame(origin, MobOrigin.byName(origin.storageKey()),
                    origin + " does not survive a write/read cycle through its own storage key");
        }
    }
}
