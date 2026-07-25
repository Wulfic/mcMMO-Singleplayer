package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats excavation} — port of legacy {@code ExcavationCommand}. Shows Giga Drill Breaker
 * duration and Archaeology (experience-orb chance + reward amount).
 */
public final class ExcavationStatsRenderer extends SkillStatsRenderer {

    private boolean canGigaDrill;
    private String gigaDrillBreakerLength;

    public ExcavationStatsRenderer() {
        super(PrimarySkillType.EXCAVATION);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canGigaDrill = hasUnlocked(SubSkillType.EXCAVATION_GIGA_DRILL_BREAKER);
        if (canGigaDrill) {
            gigaDrillBreakerLength = calculateLength(skillValue);
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        final ExcavationManager excavationManager = mmoPlayer.getExcavationManager();

        if (canGigaDrill) {
            messages.add(getStatMessage(SubSkillType.EXCAVATION_GIGA_DRILL_BREAKER,
                    gigaDrillBreakerLength));
        }
        if (hasUnlocked(SubSkillType.EXCAVATION_ARCHAEOLOGY)) {
            messages.add(getStatMessage(false, false, SubSkillType.EXCAVATION_ARCHAEOLOGY,
                    percent.format(excavationManager.getArchaelogyExperienceOrbChance() / 100.0D)));
            messages.add(getStatMessage(true, false, SubSkillType.EXCAVATION_ARCHAEOLOGY,
                    String.valueOf(excavationManager.getExperienceOrbsReward())));
        }

        return messages;
    }
}
