package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.skills.movement.MovementManager;
import com.gmail.nossr50.skills.movement.Medium;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats parkour} — the land-movement screen, and as of 2026-08-10 the largest of Agility's
 * three parents.
 *
 * <h2>What lives here, and why</h2>
 * Parkour is one of the three parents of the child skill <b>Agility</b>, and a sub-skill's parent is
 * resolved from its enum name prefix — so everything below is gated on the Parkour level
 * <em>directly</em> rather than on Agility's mean of Parkour, Swimming and Flying:
 * <ul>
 *   <li>{@link SubSkillType#PARKOUR_DODGE}, {@link SubSkillType#PARKOUR_ATHLETE} and
 *       {@link SubSkillType#PARKOUR_SMASH} — moved off Agility on 2026-08-10. All three are things
 *       you earn by running: a sprint-attack perk gated partly on how much the player swims was the
 *       same defect #4 fixed for Roll. Dodge is the one whose home was arguable (it is a combat
 *       reaction, not a way of travelling) and it is here because it has always <em>paid</em> its XP
 *       to Parkour.</li>
 *   <li>{@link SubSkillType#PARKOUR_ROLL} — moved off Agility on 2026-08-03 (GitHub #4). Fall XP is
 *       paid to Parkour, so the odds now sit next to the level that actually moves them.</li>
 *   <li>{@link SubSkillType#PARKOUR_SNOW_WALKER} — not falling through powder snow is a
 *       running-and-jumping perk, and a strong swimmer should not be handed it by the average.</li>
 * </ul>
 *
 * <p><b>Fleet Footed and Second Wind moved here on 2026-08-17</b>, when Agility was retired — this
 * paragraph used to say the opposite, that they could never live on a parent screen. Each was one
 * sub-skill carrying one rank per medium, gated on the mean of the three movement skills; each is now
 * a single-rank sub-skill of the skill that earns it, so this screen shows the <em>land</em> body of
 * both, unlocked by Parkour alone.
 *
 * <h2>Why a binary sub-skill still gets a stats line</h2>
 * Snow Walker has no magnitude — it is on or it is off — so the line pairs its {@code .Stat} label
 * with its {@code .Description} rather than a number. That is more useful than the alternative the
 * one existing binary precedent uses ({@code Fishing}'s Ice Fishing, which pairs the label with
 * itself and renders "Ice Fishing: Ice Fishing"): the player learns what the unlock actually does,
 * which for a sub-skill with no number is the only information there is to give.
 *
 * <h2>Why Roll prints two numbers</h2>
 * Graceful Roll is not a separate sub-skill — it is Roll rolled at double odds while sneaking. The
 * shipped locale has carried a {@code .Stat.Extra} ("Graceful Roll Chance") label since the Bukkit
 * port and <b>nothing had ever rendered it</b>, so the only number a player could see was the one
 * that does <em>not</em> apply when they deliberately crouch to land. That gap fed GitHub #4 being
 * reported as "rolling never procs": the doubled figure is what tells an unlucky roll from a broken
 * one, and it was not on screen anywhere.
 */
public final class ParkourStatsRenderer extends SkillStatsRenderer {

    private MovementManager agility;
    private String rollChance;
    private String gracefulRollChance;

    public ParkourStatsRenderer() {
        super(PrimarySkillType.PARKOUR);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Athlete's number comes off the manager, which reads the live config; Snow Walker is a
        // binary unlock and Smash's odds are resolved at render time like every other RNG line.
        agility = mmoPlayer.getMovementManager();

        if (!hasUnlocked(SubSkillType.PARKOUR_ROLL)) {
            return;
        }
        rollChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.PARKOUR_ROLL)[0];
        // Deliberately routed through the same helper the mechanic itself calls, rather than
        // formatting `rollChance * 2` here: if the graceful multiplier ever stops being exactly two,
        // this screen must not keep confidently printing the old relationship.
        gracefulRollChance = ProbabilityUtil.getRNGDisplayValues(
                ProbabilityUtil.getGracefulRollProbability(mmoPlayer))[0];
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.PARKOUR_DODGE)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_DODGE,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.PARKOUR_DODGE)[0]));
        }

        if (hasUnlocked(SubSkillType.PARKOUR_ROLL)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_ROLL, rollChance));
            messages.add(getStatMessage(true, false, SubSkillType.PARKOUR_ROLL,
                    gracefulRollChance));
        }

        if (hasUnlocked(SubSkillType.PARKOUR_ATHLETE) && agility != null) {
            // Shown as hunger *saved*, which is what the player feels; the manager returns the
            // multiplier that remains.
            messages.add(getStatMessage(SubSkillType.PARKOUR_ATHLETE,
                    percent.format(1.0 - agility.getAthleteExhaustionMultiplier())));
        }

        if (hasUnlocked(SubSkillType.PARKOUR_SMASH)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_SMASH,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.PARKOUR_SMASH)[0]));
        }

        if (hasUnlocked(SubSkillType.PARKOUR_SNOW_WALKER)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_SNOW_WALKER,
                    SubSkillType.PARKOUR_SNOW_WALKER.getLocaleDescription()));
        }

        // Fleet Footed and Second Wind arrived here on 2026-08-17 with the retirement of Agility.
        // Both used to be one sub-skill carrying one rank per medium, gated on the MEAN of the three
        // movement skills, so a Parkour specialist could not reach the rank for the medium they had
        // actually trained. Each is now a single-rank sub-skill of this skill, and each line below
        // shows Parkour's OWN body of it -- not a shared number repeated on three screens.
        if (agility != null && agility.canFleetFoot(Medium.LAND)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_FLEET_FOOTED,
                    percent.format(agility.getFleetFootedBonus(Medium.LAND))));
        }
        // ⚠️ The length is asked for SECOND_WIND by name. getSuperAbility(skill) is one-to-one and
        // answers null for two of the three movement skills, which would NPE rather than misprint.
        if (agility != null && agility.canSecondWind(Medium.LAND)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_SECOND_WIND,
                    calculateLength(skillValue, SuperAbilityType.SECOND_WIND)));
        }

        return messages;
    }
}
