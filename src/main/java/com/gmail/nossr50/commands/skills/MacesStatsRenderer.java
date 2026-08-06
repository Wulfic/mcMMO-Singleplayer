package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats maces} — port of legacy {@code MacesCommand}. Shows Cripple (apply chance +
 * duration) and Crush damage. Limit Break omitted (dropped from the port).
 *
 * <p>⚠️ The Cripple duration line used to read "…&e{0}s&a vs Players, &e{1}s&a vs Mobs", printing a
 * PvP number in a singleplayer mod. The "vs Players" half is gone along with the player-target
 * branch it read — see {@link MacesManager#getCrippleTickDuration()}.
 */
public final class MacesStatsRenderer extends SkillStatsRenderer {

    private boolean canCripple;
    private String crippleChanceToApply;
    private String crippleLengthMobs;

    public MacesStatsRenderer() {
        super(PrimarySkillType.MACES);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canCripple = hasUnlocked(SubSkillType.MACES_CRIPPLE);
        if (canCripple) {
            final int crippleRank = RankUtils.getRank(mmoPlayer, SubSkillType.MACES_CRIPPLE);
            crippleLengthMobs = String.valueOf(MacesManager.getCrippleTickDuration() / 20.0D);
            crippleChanceToApply =
                    McMMOMod.getAdvancedConfig().getCrippleChanceToApplyOnHit(crippleRank) + "%";
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canCripple) {
            messages.add(getStatMessage(SubSkillType.MACES_CRIPPLE, crippleChanceToApply));
            messages.add(getStatMessage(true, true, SubSkillType.MACES_CRIPPLE,
                    crippleLengthMobs));
        }
        if (hasUnlocked(SubSkillType.MACES_CRUSH)) {
            messages.add(getStatMessage(SubSkillType.MACES_CRUSH,
                    String.valueOf(mmoPlayer.getMacesManager().getCrushDamage())));
        }

        return messages;
    }
}
