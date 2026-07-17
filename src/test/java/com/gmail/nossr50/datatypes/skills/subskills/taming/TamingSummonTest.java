package com.gmail.nossr50.datatypes.skills.subskills.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the MC-free {@link TamingSummon} data holder (the {@code Math.max(_, 1)} clamps). */
class TamingSummonTest {

    @Test
    void storesConfiguredValuesVerbatim() {
        final TamingSummon summon =
                new TamingSummon(CallOfTheWildType.WOLF, "bone", 10, 1, 240, 2);

        assertEquals(CallOfTheWildType.WOLF, summon.getCallOfTheWildType());
        assertEquals("bone", summon.getItemId());
        assertEquals(10, summon.getItemAmountRequired());
        assertEquals(1, summon.getEntitiesSummoned());
        assertEquals(240, summon.getSummonLifespan());
        assertEquals(2, summon.getSummonCap());
    }

    @Test
    void clampsCostEntitiesAndCapToAtLeastOne() {
        // Legacy's Math.max(_, 1): a mis-configured zero/negative must not summon nothing or loop.
        final TamingSummon summon =
                new TamingSummon(CallOfTheWildType.HORSE, "apple", 0, -3, 0, -1);

        assertEquals(1, summon.getItemAmountRequired());
        assertEquals(1, summon.getEntitiesSummoned());
        assertEquals(1, summon.getSummonCap());
    }

    @Test
    void lifespanIsNotClamped() {
        // A lifespan of 0 means "never expires" and must be preserved, not clamped up to 1.
        final TamingSummon summon =
                new TamingSummon(CallOfTheWildType.CAT, "cod", 10, 1, 0, 1);

        assertEquals(0, summon.getSummonLifespan());
    }
}
