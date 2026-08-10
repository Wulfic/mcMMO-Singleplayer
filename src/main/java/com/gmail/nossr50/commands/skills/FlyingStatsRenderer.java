package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.agility.AgilityManager;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats flying} — the elytra screen.
 *
 * <p>Flying is one of the three parents of the child skill <b>Agility</b>. Until 2026-08-10 it owned
 * no sub-skills and fell through to {@link GenericSkillStatsRenderer}; both sub-skills below were
 * {@code AGILITY_*} constants gated on the mean of Parkour, Swimming and Flying.
 *
 * <p>This pair is the one the re-parenting mattered most for. Their unlock levels (350 and 750) were
 * read against that mean, so a player who <em>only</em> flew needed Flying 1050 and Flying 2250 to
 * reach them — both past the level cap of 1000. They were not slow to earn, they were
 * <b>unreachable</b>, and the only way to unlock a flying perk was to go running and swimming. Same
 * numbers, read against Flying itself, are now earned by flying.
 *
 * <p>Fleet Footed and Second Wind still render under {@code /mcstats agility} even though both have
 * an air body: each carries one rank <em>per medium</em>, so no single parent's level could gate all
 * three ranks. A pure flier still caps Agility at 333 and cannot reach those air ranks — that is the
 * deliberate all-rounder reward, and it is exactly why the single-medium perks had to leave.
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

        return messages;
    }
}
