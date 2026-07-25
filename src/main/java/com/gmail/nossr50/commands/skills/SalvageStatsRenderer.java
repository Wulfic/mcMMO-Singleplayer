package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats salvage} — port of legacy {@code SalvageCommand}. Shows Scrap Collector (salvage
 * item limit) and Arcane Salvage (rank + enchant extract chances).
 */
public final class SalvageStatsRenderer extends SkillStatsRenderer {

    public SalvageStatsRenderer() {
        super(PrimarySkillType.SALVAGE);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Values are read from the manager in statsDisplay.
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        final SalvageManager salvageManager = mmoPlayer.getSalvageManager();

        if (hasUnlocked(SubSkillType.SALVAGE_SCRAP_COLLECTOR)) {
            messages.add(getStatMessage(false, true, SubSkillType.SALVAGE_SCRAP_COLLECTOR,
                    String.valueOf(SalvageManager.getSalvageLimit(mmoPlayer.getPlayer())),
                    RankUtils.getHighestRankStr(SubSkillType.SALVAGE_SCRAP_COLLECTOR)));
        }
        if (hasUnlocked(SubSkillType.SALVAGE_ARCANE_SALVAGE)) {
            messages.add(getStatMessage(false, true, SubSkillType.SALVAGE_ARCANE_SALVAGE,
                    String.valueOf(salvageManager.getArcaneSalvageRank()),
                    String.valueOf(RankUtils.getHighestRank(SubSkillType.SALVAGE_ARCANE_SALVAGE))));

            if (McMMOMod.getAdvancedConfig().getArcaneSalvageEnchantLossEnabled()) {
                messages.add(LocaleLoader.getString("Ability.Generic.Template",
                        LocaleLoader.getString("Salvage.Arcane.ExtractFull"),
                        percent.format(salvageManager.getExtractFullEnchantChance() / 100)));
                messages.add(LocaleLoader.getString("Ability.Generic.Template",
                        LocaleLoader.getString("Salvage.Arcane.ExtractPartial"),
                        percent.format(salvageManager.getExtractPartialEnchantChance() / 100)));
            }
        }

        return messages;
    }
}
