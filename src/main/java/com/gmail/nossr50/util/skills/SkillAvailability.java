package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.util.MaterialMapStore;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a skill's subject matter exists in the Minecraft version this build is running against.
 *
 * <p>Distinct from {@link SkillGating}, which answers whether the <em>player</em> switched a skill
 * off (GitHub #10). This answers whether the skill could ever do anything here at all. Both feed
 * {@link SkillGating#isSkillEnabled}; only this one is beyond the player's control, which is why the
 * two stay separable and {@code /mcstats} can tell the player which of the two it hit.
 *
 * <h2>Why this exists — the owner's ruling of 2026-08-11</h2>
 *
 * Spear items ship from a Minecraft version part-way through this project's supported range. Below
 * it, {@code SPEARS} is <em>inert</em> rather than broken — {@code MaterialMapStore#isSpear} matches
 * id paths that nothing in the registry has, so nothing ever classifies as a spear and the skill
 * never fires. The ruling is that inert is not good enough: the skill is still listed by
 * {@code /mcstats}, still in the configs, and can never leave level 0. It is to be <b>disabled</b>,
 * and not dropped from {@link PrimarySkillType}.
 *
 * <h2>⚠️ Why this is a MAP and not one field per skill</h2>
 *
 * Spears was the first skill to need this and, for one release, the only one — so the gate was a
 * single {@code spearsSupported} field, justified in this javadoc by the claim that <i>"every other
 * skill's subject matter — ores, crops, mobs, the anvil — predates the floor of the supported
 * range."</i>
 *
 * <p><b>That sentence was load-bearing prose, and lowering the floor falsified it.</b>
 * {@code MACES} matches exactly one registry-id path, {@code "mace"}, so on a version without the
 * mace it is inert in precisely the way the Spears ruling exists to reject: listed by
 * {@code /mcstats}, present in the configs, permanently stuck at level 0.
 *
 * <p>A second hardcoded field would have made the next one a third, so the shape is a
 * <b>skill → required-id-paths</b> map instead. Adding a gate is one entry in {@link #GATED} and
 * nothing else: every consumer ({@code /mcstats}, the ModMenu master switches, {@link SkillGating})
 * already reads this through {@link #isSkillSupported} rather than by naming a skill.
 *
 * <h2>⚠️⚠️ Why this asks the registry instead of naming a version</h2>
 *
 * <ol>
 *   <li><b>A version-pinned constant is a claim no compiler and no test can check</b>, and this
 *       project has already shipped three of those (see {@code AGENTS.md}: <i>never pin a comment to
 *       the build's Minecraft version</i>). A hand-set per-band flag additionally has to be remembered
 *       on every band cut in future — the silent drift that branch-per-band exists in fear of.
 *       "Does this registry have a spear in it" is one expression that is correct on every band from
 *       one source tree and needs no maintenance when the next band is cut.</li>
 *   <li><b>A shipped config default cannot implement this ruling.</b> {@code copyMissingDefaults}
 *       back-fills only <em>absent</em> keys, so flipping {@code Spears.Enabled} in the jar's
 *       {@code coreskills.yml} reaches nobody who has already run the mod once on that band. The gate
 *       has to hold regardless of what is on disk, so it lives in code and is ANDed with the config
 *       rather than written into it.</li>
 * </ol>
 *
 * <h2>⚠️ Probed once, explicitly — never lazily off the first caller</h2>
 *
 * {@link #probe()} runs from {@code McMMOMod#onServerStarting}, the first point at which the item
 * registry is certainly populated. Until it runs, every skill reads as supported.
 *
 * <p>That is deliberate, and the alternative was tried first: a lazy probe on the first
 * {@link #isSkillSupported} call has to treat "the registry is empty" as "cannot tell yet", and in
 * the test suite whether the registry is populated depends on <em>which Gradle fork a test class
 * landed in</em> — a class sharing a fork with a {@code McTestRegistries.bootstrap()} sees a live
 * registry, one that does not, sees an empty one. On a band without spears that turns every
 * Spears assertion in the suite into a coin flip decided by test scheduling. An explicit probe has
 * one answer per process and nothing infers anything from an absence it did not ask for.
 */
public final class SkillAvailability {

    private SkillAvailability() {
    }

    /**
     * Every version-gated skill, and where its required registry-id paths come from.
     *
     * <p>⚠️ The value is an <em>accessor</em>, not a captured {@code Set}. The paths are read from
     * the live {@link MaterialMapStore} at probe time, so this map cannot become the second copy of
     * a list that {@link MaterialMapStore#isSpear}/{@link MaterialMapStore#isMace} classify from —
     * the drift {@link MaterialMapStore#getSpears()} exists to prevent.
     *
     * <p>🔑 <b>To gate the next skill, add one entry here.</b> {@link #isSkillSupported} treats every
     * skill absent from this map as supported, so nothing else needs an edit.
     */
    private static final Map<PrimarySkillType, Function<MaterialMapStore, Set<String>>> GATED =
            Map.of(PrimarySkillType.SPEARS, MaterialMapStore::getSpears,
                    PrimarySkillType.MACES, MaterialMapStore::getMaces);

    /**
     * The probed answer per gated skill, or {@code null} if {@link #probe()} has not run — mod init
     * before the server starts, and every unit test.
     *
     * <p>{@code volatile}, and replaced wholesale rather than mutated in place, because
     * {@link #probe()} runs on the server thread while {@link SkillGating#isSkillEnabled} is read
     * from anything that awards XP. A reader therefore sees either the whole previous answer or the
     * whole new one, never a half-filled map.
     */
    private static volatile Map<PrimarySkillType, Boolean> probed;

    /**
     * Ask this Minecraft version what it can furnish, for every skill in {@link #GATED}. Idempotent;
     * safe to call on every server start.
     *
     * <p>⚠️⚠️ <b>Refuses to answer from an empty registry.</b> "This version has no spears" and
     * "the registry has not populated" are the same observation from the outside, so an absence is
     * only evidence once {@link Materials#itemRegistryIsPopulated} says there is something to be
     * absent from. Without that check a probe that ran a moment too early would disable the skill on
     * <em>every</em> version, and would look exactly as correct in the log.
     */
    public static void probe() {
        final MaterialMapStore materials = McMMOMod.getMaterialMapStore();
        final boolean populated;
        try {
            populated = Materials.itemRegistryIsPopulated();
        } catch (Throwable probeFailed) {
            // Not expected from a running server -- the registry is up long before SERVER_STARTING --
            // but a skill silently switching off is precisely the failure this gate must not cause,
            // so it is logged loudly and every skill is left on.
            McMMOMod.LOGGER.error("Could not read the item registry to decide which skills this "
                    + "Minecraft version supports; leaving every skill enabled.", probeFailed);
            probed = null;
            return;
        }

        if (!populated) {
            McMMOMod.LOGGER.warn("The item registry was empty when mcMMO probed it, so version "
                    + "support could not be decided; leaving every skill enabled.");
            probed = null;
            return;
        }

        final Map<PrimarySkillType, Boolean> answers = new EnumMap<>(PrimarySkillType.class);
        for (Map.Entry<PrimarySkillType, Function<MaterialMapStore, Set<String>>> gate
                : GATED.entrySet()) {
            final PrimarySkillType skill = gate.getKey();
            final Set<String> requiredIdPaths = gate.getValue().apply(materials);
            final boolean supported = decide(true, requiredIdPaths, Materials::isItem);
            answers.put(skill, supported);
            // ⚠️ THE WORDING IS AN INTERFACE, NOT PROSE. scripts/gameplay_smoke_scenario.py greps
            // `Version support: <SKILL> is available|disabled` out of the boot log and cross-checks
            // it against what /mcstats lists, which is how ship-gate 6 proves the gate agrees with
            // gameplay on a band nobody can reproduce locally. It discovers the gated skills FROM
            // these lines rather than from a second hardcoded list, so the skill name must stay the
            // enum name and the two verbs must stay `available` and `disabled`.
            if (supported) {
                // Logged in both directions on purpose. A gate that is silent when it decides "on"
                // cannot be told apart, from a boot log, from a probe that never ran -- and this is
                // called from exactly one place, the sort of wiring that gets dropped in a back-port.
                McMMOMod.LOGGER.info("Version support: {} is available -- this Minecraft version "
                        + "has the items it works on.", skill.name());
            } else {
                McMMOMod.LOGGER.info("Version support: {} is disabled -- this Minecraft version has "
                        + "none of the items it works on ({}). It gains no XP, procs nothing, and is "
                        + "not listed by /mcstats. Existing levels are kept.",
                        skill.name(), String.join(", ", requiredIdPaths));
            }
        }
        probed = Collections.unmodifiableMap(answers);
    }

    /**
     * Whether {@code skill} has anything to act on in this Minecraft version.
     *
     * @param skill the skill to check; {@code null} is treated as supported
     * @return {@code false} only for a skill in {@link #GATED} that this version cannot furnish
     */
    public static boolean isSkillSupported(@Nullable PrimarySkillType skill) {
        if (skill == null || !GATED.containsKey(skill)) {
            // Not gated: this skill's subject matter exists on every version this build runs on.
            // Gating the next one is a GATED entry, not an edit here.
            return true;
        }
        final Map<PrimarySkillType, Boolean> answers = probed;
        if (answers == null) {
            // Not probed yet ⇒ no opinion ⇒ on, matching SkillGating's failure direction. Failing the
            // other way would switch a skill off in exactly the situations where nobody asked.
            return true;
        }
        return answers.getOrDefault(skill, Boolean.TRUE);
    }

    /**
     * The decision itself, separated from where its inputs come from so that both of its directions
     * are testable on every band.
     *
     * <p>That separation is the point. On the newest band the registry <em>does</em> have spears and
     * maces, so a test driven only by the live registry can never once exercise the disabling half —
     * and a gate that has never been observed to fire is not known to work.
     *
     * @param itemRegistryPopulated whether the registry has proven it populated; when it has not,
     *        an absence is not evidence and the answer is "supported"
     * @param requiredIdPaths the registry-id paths to look for; empty means there is nothing to
     *        conclude from, so again "supported"
     * @param itemExists whether a vanilla item exists for a given id path
     */
    @VisibleForTesting
    static boolean decide(boolean itemRegistryPopulated,
                          @NotNull Set<String> requiredIdPaths,
                          @NotNull Predicate<String> itemExists) {
        if (!itemRegistryPopulated || requiredIdPaths.isEmpty()) {
            return true;
        }
        return requiredIdPaths.stream().anyMatch(itemExists);
    }

    /**
     * The gated skills and their required-id-path accessors, for tests.
     *
     * <p>Exposed so a test can drive <em>every</em> gate rather than the two that existed when it was
     * written. A new {@link #GATED} entry with no matching assertion is exactly the failure a
     * hand-listed test cannot see.
     */
    @VisibleForTesting
    static @NotNull Map<PrimarySkillType, Function<MaterialMapStore, Set<String>>> gatedSkills() {
        return GATED;
    }

    /**
     * Forget every probed answer, so support reads as undecided again.
     *
     * <p>For tests only: the answers are process-wide, and one test class probing a bootstrapped
     * registry would otherwise decide them for every test that runs after it in the same fork.
     */
    @VisibleForTesting
    static void resetForTesting() {
        probed = null;
    }

    /**
     * Stand in for a probe result for one skill, so a test can hold this version at "no maces"
     * whatever this version really has.
     *
     * <p>⚠️ Not a convenience. Every band that exists today has both spears and maces, so a test
     * asserting "the gate switches the skill off when they are missing" would pass here with no gate
     * present at all — vacuous exactly where the code is developed, and load-bearing only on the band
     * nobody is looking at. This seam is what lets the wiring from {@link #isSkillSupported} through
     * {@link SkillGating#isSkillEnabled} be proven on every band instead.
     *
     * @param skill the gated skill to hold at a fixed answer
     * @param supported the answer to hold it at, or {@code null} to leave it undecided again
     */
    @VisibleForTesting
    static void setSupportedForTesting(@NotNull PrimarySkillType skill, @Nullable Boolean supported) {
        final Map<PrimarySkillType, Boolean> answers = new EnumMap<>(PrimarySkillType.class);
        final Map<PrimarySkillType, Boolean> current = probed;
        if (current != null) {
            answers.putAll(current);
        }
        if (supported == null) {
            answers.remove(skill);
        } else {
            answers.put(skill, supported);
        }
        probed = Collections.unmodifiableMap(answers);
    }
}
