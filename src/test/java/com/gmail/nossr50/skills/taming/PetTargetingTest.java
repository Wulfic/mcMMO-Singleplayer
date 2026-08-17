package com.gmail.nossr50.skills.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link PetTargeting} — the nearest-to-the-player selection rule, exercised with plain strings
 * because the rule has nothing to do with Minecraft.
 */
class PetTargetingTest {

    /** Distances keyed by name, so each case reads as a little map of the situation. */
    private static Optional<String> pick(Map<String, Double> byDistance) {
        return PetTargeting.nearestToPlayer(List.copyOf(byDistance.keySet()), byDistance::get);
    }

    @Test
    void anEmptyCandidateListPicksNothing() {
        assertTrue(PetTargeting.nearestToPlayer(List.<String>of(), s -> 0.0).isEmpty());
    }

    @Test
    void theOnlyCandidateWins() {
        assertEquals(Optional.of("zombie"),
                PetTargeting.nearestToPlayer(List.of("zombie"), s -> 400.0));
    }

    @Test
    void theNearestToThePlayerWins() {
        final List<String> candidates = List.of("far", "near", "middle");
        final Map<String, Double> distances = Map.of("far", 900.0, "near", 16.0, "middle", 100.0);

        assertEquals(Optional.of("near"),
                PetTargeting.nearestToPlayer(candidates, distances::get));
    }

    /** Order of arrival must not decide the answer — only distance may. */
    @Test
    void theNearestWinsWhereverItAppearsInTheList() {
        final Map<String, Double> distances = Map.of("a", 900.0, "b", 16.0, "c", 100.0);

        assertEquals(Optional.of("b"), PetTargeting.nearestToPlayer(List.of("a", "b", "c"), distances::get));
        assertEquals(Optional.of("b"), PetTargeting.nearestToPlayer(List.of("b", "a", "c"), distances::get));
        assertEquals(Optional.of("b"), PetTargeting.nearestToPlayer(List.of("c", "a", "b"), distances::get));
    }

    /**
     * ⚠️ Ties must be stable, and this is not a nicety. Two mobs at exactly the same distance is
     * common — a pair of zombies walking abreast — and an unstable choice would make the pack flip
     * its target every sweep and never actually close on either one. A strict {@code <} means the
     * first candidate at a given distance keeps it.
     */
    @Test
    void tiesGoToTheFirstCandidateAndStayThere() {
        final Map<String, Double> tied = Map.of("first", 64.0, "second", 64.0);

        assertEquals(Optional.of("first"),
                PetTargeting.nearestToPlayer(List.of("first", "second"), tied::get));
        // ...and re-running the identical selection gives the identical answer.
        assertEquals(Optional.of("first"),
                PetTargeting.nearestToPlayer(List.of("first", "second"), tied::get));
    }

    @Test
    void aTieAtTheFrontIsNotDisplacedByALaterEqual() {
        final Map<String, Double> distances = Map.of("a", 25.0, "b", 25.0, "c", 25.0);
        assertEquals(Optional.of("a"),
                PetTargeting.nearestToPlayer(List.of("a", "b", "c"), distances::get));
    }

    @Test
    void zeroDistanceIsAValidWinner() {
        // A mob standing inside the player's own box. Nothing should treat 0 as "no distance".
        final Map<String, Double> distances = Map.of("touching", 0.0, "other", 1.0);
        assertEquals(Optional.of("touching"),
                PetTargeting.nearestToPlayer(List.of("other", "touching"), distances::get));
    }

    @Test
    void theSelectionIsIdentityPreserving() {
        // The returned object is the caller's own candidate, not a copy or an equal — the caller
        // hands it straight to setTarget, where only identity means anything.
        final Object nearMob = new Object();
        final Object farMob = new Object();
        final Optional<Object> chosen = PetTargeting.nearestToPlayer(List.of(farMob, nearMob),
                candidate -> candidate == nearMob ? 1.0 : 900.0);

        assertSame(nearMob, chosen.orElseThrow());
    }

    @Test
    void mapDrivenCasesAgree() {
        // Uses the helper, so the helper itself is not dead code that could rot unnoticed.
        assertEquals(Optional.of("close"), pick(Map.of("close", 4.0, "distant", 4096.0)));
    }
}
