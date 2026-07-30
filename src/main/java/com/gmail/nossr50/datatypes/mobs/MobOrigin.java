package com.gmail.nossr50.datatypes.mobs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a mob came from, as far as mcMMO's anti-farm gating is concerned — the port of legacy's
 * {@code metadata.MobMetaFlagType}, which was never brought across (see
 * {@code CombatUtils#processCombatXP}'s javadoc, which still admits its mob-origin multipliers "do
 * nothing yet").
 *
 * <h2>Why this exists, and why it exists as a <em>prerequisite</em></h2>
 * Hunter's mob-mastery axis grants a <b>permanent</b> flat damage bonus at fixed per-mob kill counts
 * (Hunter D-HU1). Hand-killing to the 10,000-kill cap is roughly 28 hours; a spawner grinder produces
 * 3,000+ kills an hour, which reaches the same permanent reward in well under four. The three gates
 * {@code CombatUtils#processCombatXP} already carries — a Call-of-the-Wild summon, a player-built iron
 * golem, and an unattributed killing blow — do not close that, because a grinder you stand in and
 * swing at is player-attributed by construction. Spawn origin is the gate that does, so it lands
 * before the counter it protects rather than after.
 *
 * <h2>Absence means {@link #NATURAL}, and nothing writes NATURAL</h2>
 * A mob carries the {@code McMMOAttachments#MOB_ORIGIN} marker only when its origin
 * <em>disqualifies</em> it. That is not a space optimisation, it is the correctness property this
 * whole class rests on: {@code EntityType#create(World, SpawnReason)} is also the path taken by
 * {@code SpawnReason.LOAD} (every mob in every chunk that loads) and {@code DIMENSION_TRAVEL} (a mob
 * re-created on the far side of a portal). Both of those arrive at the same seam carrying a
 * <em>qualifying</em> reason while the entity is about to receive — or has just received — a marker
 * written in an earlier session. Writing NATURAL there would erase it. Writing nothing cannot.
 *
 * <h2>Fail closed</h2>
 * {@link #byName(String)} answers {@code null} for a value this build does not recognise and
 * {@code MobOrigins} maps that to {@link #UNKNOWN}, which does <b>not</b> count. A marker that exists
 * at all was written because something disqualified the mob; if a later version cannot read it, the
 * safe reading is "still disqualified". The opposite default would turn a renamed constant into a
 * silent re-opening of every farm this class closes.
 *
 * <p>Minecraft-free on purpose — {@code SpawnReason} never reaches this file. The mapping from
 * vanilla's reasons lives in {@code util.MobOrigins}, where it is a switch with no {@code default}
 * arm so that a Minecraft version adding a reason is a compile error rather than a silent fall-through
 * to "counts".
 */
public enum MobOrigin {

    /**
     * The world's own spawn rules, worldgen, a raid, an evoker's vexes, a zombie's reinforcements — or
     * anything mcMMO does not track. <b>The only constant that counts</b>, and the only one that is
     * never written to disk (see the class doc).
     */
    NATURAL(true),

    /**
     * A monster spawner or a trial spawner. The case the whole gate exists for: a cave-spider or
     * blaze grinder is the cheapest permanent damage buff in the game if this counts.
     */
    SPAWNER(false),

    /**
     * Born from breeding — {@code SpawnReason.BREEDING}, which in 1.21.11 is reached from roughly
     * forty {@code createChild} implementations plus shulker self-duplication. A cow pen is otherwise
     * an unbounded mastery source, and a shulker box farm an unbounded one for a hostile.
     */
    BRED(false),

    /**
     * Placed by a player rather than spawned by the world: a spawn egg, a {@code /summon}, or a
     * dispenser firing either. Mastery is meant to measure hunting, and none of this is hunting.
     */
    PLAYER_PLACED(false),

    /**
     * Placed by structure generation, or spawned by a nether portal block. This is the honest
     * modern mapping of legacy's {@code NETHER_PORTAL_MOB}: vanilla spawns portal zombified piglins
     * with {@code SpawnReason.STRUCTURE}, not with anything named after a portal.
     *
     * <p>It also catches the handful of mobs a structure places at generation time — a monument's
     * elder guardians, a mansion's evokers and vindicators, an end city's shulkers. Excluding those
     * costs nothing real: every one of them is a non-renewable one-off, so no player was ever going
     * to reach a 500-kill threshold on them anyway.
     */
    STRUCTURE(false),

    /**
     * A marker this build cannot interpret. Never written — only produced when reading a value that
     * {@link #byName(String)} does not recognise, which today can only happen on a downgrade. Does
     * not count, deliberately; see "Fail closed" in the class doc.
     */
    UNKNOWN(false);

    private final boolean countsTowardMastery;

    MobOrigin(boolean countsTowardMastery) {
        this.countsTowardMastery = countsTowardMastery;
    }

    /**
     * Whether a kill of a mob with this origin may advance Hunter's per-mob mastery counter.
     *
     * <p>Deliberately narrower than the gate on Hunter <em>XP</em>, which keeps the looser
     * {@code processCombatXP} checks it already has (D-HU1: "it is entirely reasonable for a spawner
     * zombie to pay XP but not mastery, and that is the recommended split"). XP is a rate; mastery is
     * permanent, and only the permanent thing needs the strict gate.
     */
    public boolean countsTowardMastery() {
        return countsTowardMastery;
    }

    /** The value written to the mob's NBT. {@link #name()} is the on-disk key. */
    public @NotNull String storageKey() {
        return name();
    }

    /**
     * Resolves a stored marker, or {@code null} if this build does not recognise it.
     *
     * <p>Resolution is deliberately deferred to read time rather than done as the attachment is
     * decoded: an unrecognised value must degrade to {@link #UNKNOWN} for one mob, never throw and
     * take a chunk's entity list with it. That is the same rule D-HU2 sets for the kill-count map
     * ("never resolve the key to an {@code EntityType} at load time"), and the reason for it is
     * {@code isIn(TagKey)} throwing on unbound tags.
     */
    public static @Nullable MobOrigin byName(@Nullable String storedValue) {
        if (storedValue == null) {
            return null;
        }
        for (MobOrigin origin : values()) {
            if (origin.name().equals(storedValue)) {
                return origin;
            }
        }
        return null;
    }
}
