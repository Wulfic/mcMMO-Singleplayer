package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats tridents} — port of legacy {@code TridentsCommand}. Shows the Impale damage bonus.
 * Limit Break omitted (dropped from the port).
 */
public final class TridentsStatsRenderer extends SkillStatsRenderer {

    public TridentsStatsRenderer() {
        super(PrimarySkillType.TRIDENTS);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Impale damage is read from the manager in statsDisplay.
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.TRIDENTS_IMPALE)) {
            messages.add(getStatMessage(SubSkillType.TRIDENTS_IMPALE,
                    String.valueOf(mmoPlayer.getTridentsManager().impaleDamageBonus())));
        }

        return messages;
    }
}
