package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.config.CoreSkillsConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The single authority on whether a skill is switched on (GitHub #10).
 *
 * <p>{@code coreskills.yml} has carried a {@code <Skill>.Enabled} key, and
 * {@link CoreSkillsConfig#isPrimarySkillEnabled} has been able to read it, since the Phase 8 config
 * port — but <b>nothing ever called it</b>. Setting a skill to {@code false} did precisely nothing.
 * This class is what turns that key into behaviour.
 *
 * <h2>Why the enforcement is spread over several chokepoints</h2>
 *
 * "Disabled" has to mean all six of: no XP, no sub-skill procs, no super ability, no XP bar, no
 * {@code /mcstats} section, no milestone plaques. There is no one method every one of those passes
 * through, so each is gated where it funnels:
 *
 * <ol>
 *   <li><b>XP</b> — {@code McMMOPlayer#beginXpGain}, the single entry point every award reaches
 *       (skill managers go through {@code SkillManager#applyXpGain}, which delegates to it). Closing
 *       this also closes level-ups, and therefore the XP bar and the milestone plaques, for free.</li>
 *   <li><b>Sub-skill procs</b> — {@link Permissions#isSubSkillEnabled} /
 *       {@link Permissions#canUseSubSkill}, {@link RankUtils#hasUnlockedSubskill} /
 *       {@link RankUtils#hasReachedRank}, and
 *       {@code ProbabilityUtil#isSkillRNGSuccessful}. Between them all 23 skill managers are covered;
 *       no single one of them is, which is why all five are gated rather than the tidiest one.</li>
 *   <li><b>Super abilities</b> — the activation and readying paths in {@code SuperAbilityListener},
 *       plus the per-ability {@code Permissions} predicates.</li>
 *   <li><b>{@code /mcstats}</b> — the listing loop and the per-skill screen.</li>
 *   <li><b>Plaques</b> — {@code McMMOPlayer#snapshotMilestones}, because a <em>child</em> skill
 *       levels without ever touching the XP path and would otherwise still plaque while disabled.</li>
 * </ol>
 *
 * <h2>⚠️ Why the rank is never forced to zero</h2>
 *
 * The obvious shortcut — make {@link RankUtils#getRank} answer 0 for a disabled skill and let every
 * rank gate fail on its own — is a trap, and this codebase has stepped on it three times already
 * (§F #9, Cripple, and Spears' Momentum). Several {@code AdvancedConfig} getters index a defaults
 * array by {@code rank - 1} while evaluating the fallback <em>eagerly</em>, so they throw
 * {@code ArrayIndexOutOfBoundsException} at rank 0. A fresh player never reaches those call sites
 * because an outer gate checks their skill <em>level</em> first — but a level-800 player who
 * disables the skill keeps that level (disabling is a pause, not a reset), sails through the outer
 * gate, and lands on the landmine.
 *
 * <p>So the gating is on the <em>boolean</em> predicates only. {@code getRank} keeps telling the
 * truth, and nothing downstream is handed a number it was never written to accept.
 *
 * <h2>Failure direction</h2>
 *
 * Every method here answers "enabled" when it cannot tell — no config wired (unit tests, the
 * headless boot, between world sessions), no {@code SkillTools}, an unmapped sub-skill. Failing
 * closed would silently switch the entire mod off in exactly the situations where nobody asked for
 * anything to be off.
 */
public final class SkillGating {

    private SkillGating() {
    }

    /**
     * Whether {@code skill} is switched on in {@code coreskills.yml}.
     *
     * @param skill the skill to check; {@code null} is treated as enabled
     * @return {@code true} unless the player has explicitly disabled this skill
     */
    public static boolean isSkillEnabled(@Nullable PrimarySkillType skill) {
        if (skill == null) {
            return true;
        }
        final CoreSkillsConfig config = McMMOMod.getCoreSkillsConfig();
        // No config ⇒ no opinion ⇒ on. See the failure-direction note on the class.
        return config == null || config.isPrimarySkillEnabled(skill);
    }

    /**
     * Whether {@code subSkillType}'s parent skill is switched on.
     *
     * @param subSkillType the sub-skill to check; {@code null} is treated as enabled
     * @return {@code true} unless the player has explicitly disabled the parent skill
     */
    public static boolean isSubSkillEnabled(@Nullable SubSkillType subSkillType) {
        return subSkillType == null || isSkillEnabled(parentOf(subSkillType));
    }

    /**
     * {@code subSkillType}'s parent, or {@code null} when it has none mapped.
     *
     * <p>Goes through {@link SkillTools} rather than {@link SubSkillType#getParentSkill()} only to
     * keep the {@code null} explicit at the one place that has to cope with it: the parent map is
     * built from the enum-name prefix, so a sub-skill whose prefix matches no primary skill is
     * absent from it, and this must not become an NPE on a hot proc path.
     */
    private static @Nullable PrimarySkillType parentOf(@NotNull SubSkillType subSkillType) {
        return McMMOMod.getSkillTools().getPrimarySkillBySubSkill(subSkillType);
    }
}
