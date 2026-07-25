package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats spears} — port of legacy {@code SpearsCommand}. Shows Spear Mastery bonus damage
 * and Momentum (apply chance + duration). Limit Break omitted (dropped from the port).
 */
public final class SpearsStatsRenderer extends SkillStatsRenderer {

    private boolean canMomentum;
    private String momentumChanceToApply;
    private String momentumDuration;

    public SpearsStatsRenderer() {
        super(PrimarySkillType.SPEARS);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canMomentum = hasUnlocked(SubSkillType.SPEARS_MOMENTUM);
        if (canMomentum) {
            final int momentumRank = RankUtils.getRank(mmoPlayer, SubSkillType.SPEARS_MOMENTUM);
            momentumDuration =
                    String.valueOf(SpearsManager.getMomentumTickDuration(momentumRank) / 20.0D);
            momentumChanceToApply =
                    McMMOMod.getAdvancedConfig().getMomentumChanceToApplyOnHit(momentumRank) + "%";
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.SPEARS_SPEAR_MASTERY)) {
            messages.add(getStatMessage(SubSkillType.SPEARS_SPEAR_MASTERY, String.format("%.2f",
                    mmoPlayer.getSpearsManager().getSpearMasteryBonusDamage())));
        }
        if (canMomentum) {
            messages.add(getStatMessage(SubSkillType.SPEARS_MOMENTUM, momentumChanceToApply));
            messages.add(getStatMessage(true, true, SubSkillType.SPEARS_MOMENTUM, momentumDuration));
        }

        return messages;
    }
}
