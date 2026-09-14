package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * <b>Every {@link PrimarySkillType} must carry a recorded version-gating decision</b> — TODO §64.2,
 * closing <b>risk R12, residual 1</b>.
 *
 * <h2>The residual</h2>
 * The register recorded it exactly: <i>"the map is still a hand-maintained list; a NEW skill whose
 * items postdate the floor is added to {@link PrimarySkillType} and to nothing else, and nothing
 * goes red. Auditing skills against required ids is not yet mechanical."</i>
 *
 * <p>{@link SkillAvailability#isSkillSupported} treats any skill absent from {@code GATED} as
 * supported. That is the correct runtime behaviour and it was also the entire record of the
 * decision — so "nobody thought about it" and "we decided it needs no gate" were the same state,
 * indistinguishable to every gate in this repo. Adding {@code SkillAvailability.UNGATED} makes them
 * different states; this test is what makes the difference cost something.
 *
 * <h2>⚠️ Why this is not simply a test over the live enum</h2>
 * A test that walks {@link PrimarySkillType#values()} and checks the two live sets can pass forever
 * without ever having been <em>able</em> to fail, because the only input that would redden it — an
 * enum constant nobody classified — cannot be manufactured at runtime. This repo has caught that
 * shape roughly seventeen times, most recently in §61.6, and the rule it arrived at is that a
 * one-sided guard pair proves only that it can say NO.
 *
 * <p>So the partition check is a <b>pure function over an injected universe</b>. The real assertion
 * feeds it the live enum; the rejection cases feed it a universe with a constant deliberately left
 * out of both sets, which is precisely the state a newly-added skill creates. The function under
 * test is the same one in both.
 *
 * <h2>✅ It found something on its first run, and the cause generalises</h2>
 * The initial {@code UNGATED} set was built by grepping {@link PrimarySkillType}'s source with
 * {@code ^\s{4}[A-Z_]+\s*[(,;]} — which silently dropped <b>{@code WOODCUTTING}</b>, the last
 * constant, because it is written with no trailing {@code ,} or {@code ;} before the closing brace.
 * The enum has <b>26</b> constants; the regex reported 25, and 25 looked entirely plausible.
 *
 * <p>🔑 <b>A count derived from a regex over source is a lower bound, not a count</b> — the same
 * lesson §63 recorded for tag listings and §50 for config ids, arriving here from a third
 * direction. The authoritative reads are {@link PrimarySkillType#values()} at runtime, which is
 * what this test uses, and {@code javap} over the compiled enum, which is what settled it. Nothing
 * else in the repo would have reported the omission: a skill missing from {@code UNGATED} is
 * treated as supported, which is what it already was.
 *
 * <h2>⚠️ What being ungated does and does not claim</h2>
 * That the skill's items or blocks exist on <em>every</em> version in the supported range — never
 * that the skill is finished or wired. {@code STEALTH} is half-built and correctly ungated.
 *
 * <p>🔑 <b>And a note on why the live half is weak on its own:</b> {@code MACES} is inert on every
 * in-scope version — R-x withdrew the {@code 1.20} line and {@code Items.MACE} ships from
 * {@code 1.20.5} — so the only gated skill that can still fire on a real band is {@code SPEARS}.
 * The gating mechanism is therefore exercised far less by reality than the two-entry map suggests,
 * which is another reason the proof here is mechanical rather than observational.
 */
class SkillGatePartitionTest {

    /** One skill's classification problem, in words a reader can act on. */
    record Unclassified(PrimarySkillType skill, String problem) {}

    // -------------------------------------------------------------------------------------------
    // The pure half.
    // -------------------------------------------------------------------------------------------

    /**
     * Every skill in {@code universe} that is not in exactly one of {@code gated} / {@code ungated}.
     *
     * @param universe every skill that must be classified — the live enum in the real assertion
     * @param gated    the skills with a version gate
     * @param ungated  the skills explicitly declared as needing none
     */
    static List<Unclassified> partitionFailures(Collection<PrimarySkillType> universe,
                                                Set<PrimarySkillType> gated,
                                                Set<PrimarySkillType> ungated) {
        final List<Unclassified> failures = new ArrayList<>();
        for (PrimarySkillType skill : universe) {
            final boolean isGated = gated.contains(skill);
            final boolean isUngated = ungated.contains(skill);
            if (isGated && isUngated) {
                failures.add(new Unclassified(skill, skill
                        + " is in BOTH SkillAvailability.GATED and SkillAvailability.UNGATED. It "
                        + "cannot be both; remove whichever entry is wrong"));
            } else if (!isGated && !isUngated) {
                failures.add(new Unclassified(skill, skill
                        + " has no recorded version-gating decision. It is a PrimarySkillType "
                        + "constant that appears in neither SkillAvailability.GATED nor "
                        + "SkillAvailability.UNGATED, so it is treated as supported on every "
                        + "version BY DEFAULT rather than by a decision. Add it to UNGATED if its "
                        + "items exist on every version in supported_minecraft_versions, or to "
                        + "GATED with an accessor naming the registry-id paths it needs"));
            }
        }
        // A classification for a skill that no longer exists is dead weight that reads as coverage.
        for (PrimarySkillType stale : union(gated, ungated)) {
            if (!universe.contains(stale)) {
                failures.add(new Unclassified(stale, stale
                        + " is classified but is not in the universe being audited -- a stale entry"));
            }
        }
        return failures;
    }

    private static Set<PrimarySkillType> union(Set<PrimarySkillType> a, Set<PrimarySkillType> b) {
        final EnumSet<PrimarySkillType> all = EnumSet.noneOf(PrimarySkillType.class);
        all.addAll(a);
        all.addAll(b);
        return all;
    }

    // -------------------------------------------------------------------------------------------
    // The real assertion.
    // -------------------------------------------------------------------------------------------

    @Test
    void every_primary_skill_carries_a_recorded_gating_decision() {
        final List<Unclassified> failures = partitionFailures(
                List.of(PrimarySkillType.values()),
                SkillAvailability.gatedSkills().keySet(),
                SkillAvailability.acknowledgedUngatedSkills());
        assertEquals(List.of(), failures,
                "PrimarySkillType has constants with no version-gating decision recorded. "
                        + "Risk R12 residual 1 is exactly this: a skill added to the enum and to "
                        + "nothing else is silently treated as supported everywhere.");
    }

    /** The two sets must together account for the enum, with nothing invented and nothing left. */
    @Test
    void the_two_sets_exactly_cover_the_enum() {
        assertEquals(EnumSet.allOf(PrimarySkillType.class),
                union(SkillAvailability.gatedSkills().keySet(),
                        SkillAvailability.acknowledgedUngatedSkills()),
                "the union of GATED and UNGATED is not PrimarySkillType.values()");
    }

    // -------------------------------------------------------------------------------------------
    // Proof the guard can say NO. The universe is injected precisely so the state a NEW enum
    // constant creates can be manufactured -- it cannot be, at runtime, on the live enum.
    // -------------------------------------------------------------------------------------------

    /**
     * 🔴 <b>The case this whole section exists for.</b> A skill present in the enum and in neither
     * set — byte-for-byte the state produced by adding a constant to {@link PrimarySkillType} and
     * stopping there.
     */
    @Test
    void a_skill_in_neither_set_is_reported() {
        final EnumSet<PrimarySkillType> ungatedMinusOne =
                EnumSet.copyOf(SkillAvailability.acknowledgedUngatedSkills());
        ungatedMinusOne.remove(PrimarySkillType.MINING);

        final List<Unclassified> failures = partitionFailures(
                List.of(PrimarySkillType.values()),
                SkillAvailability.gatedSkills().keySet(),
                ungatedMinusOne);

        assertEquals(1, failures.size(), "expected exactly one unclassified skill, got " + failures);
        assertEquals(PrimarySkillType.MINING, failures.get(0).skill());
        assertTrue(failures.get(0).problem().contains("no recorded version-gating decision"),
                failures.get(0).problem());
    }

    /** The message has to name the constant and both remedies, or it trains people to guess. */
    @Test
    void the_failure_message_names_the_skill_and_both_remedies() {
        final EnumSet<PrimarySkillType> ungatedMinusOne =
                EnumSet.copyOf(SkillAvailability.acknowledgedUngatedSkills());
        ungatedMinusOne.remove(PrimarySkillType.COOKING);
        final String problem = partitionFailures(
                List.of(PrimarySkillType.values()),
                SkillAvailability.gatedSkills().keySet(),
                ungatedMinusOne).get(0).problem();

        assertTrue(problem.contains("COOKING"), problem);
        assertTrue(problem.contains("UNGATED"), problem);
        assertTrue(problem.contains("GATED"), problem);
    }

    @Test
    void a_skill_in_both_sets_is_reported() {
        final EnumSet<PrimarySkillType> ungatedPlusGated =
                EnumSet.copyOf(SkillAvailability.acknowledgedUngatedSkills());
        ungatedPlusGated.add(PrimarySkillType.SPEARS);

        final List<Unclassified> failures = partitionFailures(
                List.of(PrimarySkillType.values()),
                SkillAvailability.gatedSkills().keySet(),
                ungatedPlusGated);

        assertEquals(1, failures.size(), failures.toString());
        assertEquals(PrimarySkillType.SPEARS, failures.get(0).skill());
        assertTrue(failures.get(0).problem().contains("BOTH"), failures.get(0).problem());
    }

    @Test
    void a_classification_for_a_skill_that_no_longer_exists_is_reported() {
        final List<PrimarySkillType> shrunkUniverse = new ArrayList<>(
                EnumSet.allOf(PrimarySkillType.class));
        shrunkUniverse.remove(PrimarySkillType.SPEARS);

        final List<Unclassified> failures = partitionFailures(
                shrunkUniverse,
                SkillAvailability.gatedSkills().keySet(),
                SkillAvailability.acknowledgedUngatedSkills());

        assertFalse(failures.isEmpty(), "a stale classification was not reported");
        assertTrue(failures.stream().anyMatch(f -> f.problem().contains("stale entry")),
                failures.toString());
    }

    /** An empty universe must report the whole classified set as stale, never silently pass. */
    @Test
    void an_empty_universe_does_not_read_as_clean() {
        assertFalse(partitionFailures(List.of(),
                        SkillAvailability.gatedSkills().keySet(),
                        SkillAvailability.acknowledgedUngatedSkills()).isEmpty(),
                "auditing nothing returned no failures, which is how a broken audit looks "
                        + "identical to a clean one");
    }

    // -------------------------------------------------------------------------------------------
    // The gated half still has to be real.
    // -------------------------------------------------------------------------------------------

    /**
     * ⚠️ {@code UNGATED} is a claim, and a claim that everything is ungated would satisfy the
     * partition perfectly while deleting the mechanism. At least one skill must still be gated.
     */
    @Test
    void the_gated_set_is_not_empty() {
        assertFalse(SkillAvailability.gatedSkills().isEmpty(),
                "every skill is declared ungated, so the partition passes and the version-gating "
                        + "mechanism is dead code. UNGATED is an acknowledgement, not an opt-out");
    }
}
