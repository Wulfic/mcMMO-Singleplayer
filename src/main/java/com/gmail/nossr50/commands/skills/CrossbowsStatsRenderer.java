package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats crossbows} — port of legacy {@code CrossbowsCommand}. Shows Powered Shot damage
 * bonus and Trick Shot max bounce count. Limit Break omitted (dropped from the port).
 */
public final class CrossbowsStatsRenderer extends SkillStatsRenderer {

    public CrossbowsStatsRenderer() {
        super(PrimarySkillType.CROSSBOWS);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Values are read straight from the manager in statsDisplay.
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.CROSSBOWS_POWERED_SHOT)) {
            messages.add(getStatMessage(SubSkillType.CROSSBOWS_POWERED_SHOT, percent.format(
                    mmoPlayer.getCrossbowsManager().getDamageBonusPercent(mmoPlayer.getPlayer()))));
        }
        if (hasUnlocked(SubSkillType.CROSSBOWS_TRICK_SHOT)) {
            messages.add(getStatMessage(SubSkillType.CROSSBOWS_TRICK_SHOT, String.valueOf(
                    mmoPlayer.getCrossbowsManager().getTrickShotMaxBounceCount())));
        }

        return messages;
    }
}
