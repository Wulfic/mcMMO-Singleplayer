package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.agility.Medium;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats flying} — the elytra screen.
 *
 * <p>Flying is one of the three parents of the child skill <b>Agility</b>. Until 2026-08-10 it owned
 * no sub-skills and fell through to {@link GenericSkillStatsRenderer}; both sub-skills below were
 * {@code AGILITY_*} constants gated on the mean of Parkour, Swimming and Flying, back when a
 * retired {@code AGILITY} child skill held that mean.
 *
 * <p>This pair is the one the re-parenting mattered most for. Their unlock levels (350 and 750) were
 * read against that mean, so a player who <em>only</em> flew needed Flying 1050 and Flying 2250 to
 * reach them — both past the level cap of 1000. They were not slow to earn, they were
 * <b>unreachable</b>, and the only way to unlock a flying perk was to go running and swimming. Same
 * numbers, read against Flying itself, are now earned by flying.
 *
 * <p><b>Fleet Footed and Second Wind moved here on 2026-08-17</b>, when Agility was retired. Each was
 * one sub-skill carrying one rank per medium, gated on the mean of the three movement skills; each is
 * now a single-rank sub-skill of the skill that earns it, so this screen shows the <em>air</em> body
 * of both. ⚠️ This reverses what used to be documented here: a pure flier capped Agility at 333 and
 * could <b>never</b> reach the air ranks — the deliberate all-rounder reward — and that gate is gone.
 * A specialist now unlocks their own medium's perks at their own skill's level.
 */
public final class FlyingStatsRenderer extends SkillStatsRenderer {

    private AgilityManager agility;

    public FlyingStatsRenderer() {
        super(PrimarySkillType.FLYING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Both lines are magnitudes off the manager, which reads the live config; neither is RNG.
        agility = mmoPlayer.getAgilityManager();
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        if (agility == null) {
            return messages;
        }

        if (hasUnlocked(SubSkillType.FLYING_GLIDE)) {
            messages.add(getStatMessage(SubSkillType.FLYING_GLIDE,
                    percent.format(agility.getGlideDescentReduction())));
        }

        if (hasUnlocked(SubSkillType.FLYING_SOLAR_WINGS)) {
            messages.add(getStatMessage(SubSkillType.FLYING_SOLAR_WINGS,
                    agility.getSolarWingsRepairAmount(false) + " per "
                            + Math.max(1, agility.getSolarWingsIntervalTicks() / 20) + "s"));
        }

        // Fleet Footed and Second Wind arrived here on 2026-08-17 with the retirement of Agility.
        // Both used to be one sub-skill carrying one rank per medium, gated on the MEAN of the three
        // movement skills, so a Flying specialist could not reach the rank for the medium they had
        // actually trained. Each is now a single-rank sub-skill of this skill, and each line below
        // shows Flying's OWN body of it -- not a shared number repeated on three screens.
        if (agility != null && agility.canFleetFoot(Medium.AIR)) {
            messages.add(getStatMessage(SubSkillType.FLYING_FLEET_FOOTED,
                    percent.format(agility.getFleetFootedBonus(Medium.AIR))));
        }
        // ⚠️ The length is asked for SECOND_WIND by name. getSuperAbility(skill) is one-to-one and
        // answers null for two of the three movement skills, which would NPE rather than misprint.
        if (agility != null && agility.canSecondWind(Medium.AIR)) {
            messages.add(getStatMessage(SubSkillType.FLYING_SECOND_WIND,
                    calculateLength(skillValue, SuperAbilityType.SECOND_WIND)));
        }

        return messages;
    }
}
