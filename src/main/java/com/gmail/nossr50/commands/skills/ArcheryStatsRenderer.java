package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.archery.Archery;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats archery} — port of legacy {@code ArcheryCommand}. Shows Arrow Retrieval and Daze
 * chances plus Skill Shot damage bonus. Limit Break omitted (dropped from the port).
 */
public final class ArcheryStatsRenderer extends SkillStatsRenderer {

    private boolean canRetrieve;
    private boolean canDaze;
    private boolean canSkillShot;

    private String retrieveChance;
    private String dazeChance;
    private String skillShotBonus;

    public ArcheryStatsRenderer() {
        super(PrimarySkillType.ARCHERY);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canRetrieve = hasUnlocked(SubSkillType.ARCHERY_ARROW_RETRIEVAL);
        canDaze = hasUnlocked(SubSkillType.ARCHERY_DAZE);
        canSkillShot = hasUnlocked(SubSkillType.ARCHERY_SKILL_SHOT);

        if (canRetrieve) {
            retrieveChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.ARCHERY_ARROW_RETRIEVAL)[0];
        }
        if (canDaze) {
            dazeChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.ARCHERY_DAZE)[0];
        }
        if (canSkillShot) {
            skillShotBonus = percent.format(Archery.getDamageBonusPercent(mmoPlayer.getPlayer()));
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canRetrieve) {
            messages.add(getStatMessage(SubSkillType.ARCHERY_ARROW_RETRIEVAL, retrieveChance));
        }
        if (canDaze) {
            messages.add(getStatMessage(SubSkillType.ARCHERY_DAZE, dazeChance));
        }
        if (canSkillShot) {
            messages.add(getStatMessage(SubSkillType.ARCHERY_SKILL_SHOT, skillShotBonus));
        }

        return messages;
    }
}
