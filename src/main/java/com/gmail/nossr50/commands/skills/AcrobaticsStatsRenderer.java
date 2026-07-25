package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats acrobatics} — port of legacy {@code AcrobaticsCommand}. Shows Dodge and Roll
 * chances. (Legacy gated Roll on the dropped {@code AbstractSubSkill}/{@code InteractionManager}
 * registry; singleplayer shows it directly when unlocked.)
 */
public final class AcrobaticsStatsRenderer extends SkillStatsRenderer {

    private boolean canDodge;
    private boolean canRoll;
    private String dodgeChance;
    private String rollChance;

    public AcrobaticsStatsRenderer() {
        super(PrimarySkillType.ACROBATICS);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canDodge = hasUnlocked(SubSkillType.ACROBATICS_DODGE);
        canRoll = hasUnlocked(SubSkillType.ACROBATICS_ROLL);

        if (canDodge) {
            dodgeChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.ACROBATICS_DODGE)[0];
        }
        if (canRoll) {
            rollChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.ACROBATICS_ROLL)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canDodge) {
            messages.add(getStatMessage(SubSkillType.ACROBATICS_DODGE, dodgeChance));
        }
        if (canRoll) {
            messages.add(getStatMessage(SubSkillType.ACROBATICS_ROLL, rollChance));
        }

        return messages;
    }
}
