package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats repair} — port of legacy {@code RepairCommand}. Shows Arcane Forging (rank +
 * enchant keep/downgrade chances), Repair Mastery bonus, and Super Repair chance.
 */
public final class RepairStatsRenderer extends SkillStatsRenderer {

    private boolean canArcaneForge;
    private boolean canMasterRepair;
    private boolean canSuperRepair;
    private String repairMasteryBonus;
    private String superRepairChance;

    public RepairStatsRenderer() {
        super(PrimarySkillType.REPAIR);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canArcaneForge = hasUnlocked(SubSkillType.REPAIR_ARCANE_FORGING);
        canMasterRepair = hasUnlocked(SubSkillType.REPAIR_REPAIR_MASTERY);
        canSuperRepair = hasUnlocked(SubSkillType.REPAIR_SUPER_REPAIR);

        if (canMasterRepair) {
            final double maxBonus = McMMOMod.getAdvancedConfig().getRepairMasteryMaxBonus();
            final int maxBonusLevel = McMMOMod.getAdvancedConfig()
                    .getMaxBonusLevel(SubSkillType.REPAIR_REPAIR_MASTERY);
            repairMasteryBonus = percent.format(
                    Math.min((maxBonus / maxBonusLevel) * skillValue, maxBonus) / 100D);
        }
        if (canSuperRepair) {
            superRepairChance = ProbabilityUtil.getRNGDisplayValues(
                    mmoPlayer, SubSkillType.REPAIR_SUPER_REPAIR)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canArcaneForge) {
            final RepairManager repairManager = mmoPlayer.getRepairManager();
            messages.add(getStatMessage(false, true, SubSkillType.REPAIR_ARCANE_FORGING,
                    String.valueOf(RankUtils.getRank(mmoPlayer, SubSkillType.REPAIR_ARCANE_FORGING)),
                    RankUtils.getHighestRankStr(SubSkillType.REPAIR_ARCANE_FORGING)));

            if (McMMOMod.getAdvancedConfig().getArcaneForgingEnchantLossEnabled()) {
                // Singleplayer has no arcane-bypass perk, so the live keep/downgrade chances apply.
                messages.add(getStatMessage(true, true, SubSkillType.REPAIR_ARCANE_FORGING,
                        String.valueOf(repairManager.getKeepEnchantChance()),
                        String.valueOf(repairManager.getDowngradeEnchantChance())));
            }
        }
        if (canMasterRepair) {
            messages.add(getStatMessage(false, true, SubSkillType.REPAIR_REPAIR_MASTERY,
                    repairMasteryBonus));
        }
        if (canSuperRepair) {
            messages.add(getStatMessage(SubSkillType.REPAIR_SUPER_REPAIR, superRepairChance));
        }

        return messages;
    }
}
