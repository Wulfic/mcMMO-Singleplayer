package com.gmail.nossr50.skills.taming;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import org.jetbrains.annotations.NotNull;

/**
 * Which of several candidate hostiles an aggressive-mode pet goes for.
 *
 * <h2>Nearest to the PLAYER, not to the pet (ruling R-5)</h2>
 * That is the whole content of this class, and it is a deliberate choice rather than the obvious one.
 * Measuring from the player means the pack converges on one threat — the one closest to the person
 * they are guarding — instead of each pet wandering off after whatever happens to be nearest to
 * itself. It also makes the answer independent of where the pets happen to be standing, so the same
 * sweep produces the same decision for every pet in the pack and the whole thing costs one sort key
 * rather than one per pet.
 *
 * <p>Minecraft-free on purpose: the caller has already decided <em>who is eligible</em> (a
 * {@code Monster} the wolf's own {@code canAttackWithOwner} permits, not already engaged, not the
 * warden) and hands this the survivors with their distances. Splitting it here is what lets the rule
 * be tested without a world — the eligibility half needs mobs, the selection half does not.
 */
public final class PetTargeting {

    private PetTargeting() {
    }

    /**
     * The candidate closest to the player, or empty when there are none.
     *
     * <p><b>Ties are stable</b>: a strict {@code <} means the first candidate at a given distance
     * wins and later equals do not displace it. That matters more than it looks — two mobs at exactly
     * the same distance is common (a pair of zombies walking abreast), and an unstable choice would
     * make the pack flip its target every sweep and never close on either. The caller's iteration
     * order is therefore the tiebreak, which for a world entity query is stable within a tick.
     *
     * @param candidates       the eligible hostiles; may be empty
     * @param squaredDistance  each candidate's <em>squared</em> distance to the player — squared
     *                         because the caller has it for free from {@code squaredDistanceTo} and
     *                         a square root would change no ordering
     */
    public static <T> @NotNull Optional<T> nearestToPlayer(@NotNull List<T> candidates,
            @NotNull ToDoubleFunction<T> squaredDistance) {
        T best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            final double distance = squaredDistance.applyAsDouble(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
