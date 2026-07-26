package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats agility} — port of legacy {@code AgilityCommand}. Shows Dodge and Roll
 * chances. (Legacy gated Roll on the dropped {@code AbstractSubSkill}/{@code InteractionManager}
 * registry; singleplayer shows it directly when unlocked.)
 */
public final class AgilityStatsRenderer extends SkillStatsRenderer {

    private boolean canDodge;
    private boolean canRoll;
    private String dodgeChance;
    private String rollChance;

    public AgilityStatsRenderer() {
        super(PrimarySkillType.AGILITY);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canDodge = hasUnlocked(SubSkillType.AGILITY_DODGE);
        canRoll = hasUnlocked(SubSkillType.AGILITY_ROLL);

        if (canDodge) {
            dodgeChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.AGILITY_DODGE)[0];
        }
        if (canRoll) {
            rollChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.AGILITY_ROLL)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canDodge) {
            messages.add(getStatMessage(SubSkillType.AGILITY_DODGE, dodgeChance));
        }
        if (canRoll) {
            messages.add(getStatMessage(SubSkillType.AGILITY_ROLL, rollChance));
        }

        return messages;
    }
}
