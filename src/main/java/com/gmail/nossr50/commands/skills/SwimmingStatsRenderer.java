package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats swimming} — the water screen.
 *
 * <p>Swimming is one of the three parents of the child skill <b>Agility</b>. Until 2026-08-10 it
 * owned no sub-skills at all and fell through to {@link GenericSkillStatsRenderer}, which showed a
 * level and nothing else; both sub-skills below were {@code AGILITY_*} constants gated on the mean of
 * Parkour, Swimming and Flying. Holding your breath and turning up treasure in silt are earned by
 * swimming, so they are gated on this level now and their numbers belong on this screen.
 *
 * <p>Fleet Footed and Second Wind still render under {@code /mcstats agility} even though both have
 * a water body: each carries one rank <em>per medium</em>, so no single parent's level could gate
 * all three ranks.
 */
public final class SwimmingStatsRenderer extends SkillStatsRenderer {

    private AgilityManager agility;

    public SwimmingStatsRenderer() {
        super(PrimarySkillType.SWIMMING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Lead Lungs' magnitude comes off the manager (it reads the live config and the level
        // ladder); Lake Raider is an RNG line resolved at render time like every other one.
        agility = mmoPlayer.getAgilityManager();
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.SWIMMING_LEAD_LUNGS) && agility != null) {
            // Vanilla spends exactly one air per tick, so the per-tick top-up reads naturally as the
            // fraction of your breath that is being given back.
            messages.add(getStatMessage(SubSkillType.SWIMMING_LEAD_LUNGS,
                    percent.format(agility.getLeadLungsAirTopUpPerTick())));
        }

        if (hasUnlocked(SubSkillType.SWIMMING_LAKE_RAIDER)) {
            messages.add(getStatMessage(SubSkillType.SWIMMING_LAKE_RAIDER,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                            SubSkillType.SWIMMING_LAKE_RAIDER)[0]));
        }

        return messages;
    }
}
