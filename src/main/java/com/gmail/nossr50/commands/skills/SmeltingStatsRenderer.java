package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats smelting} — port of legacy {@code SmeltingCommand}. Shows Fuel Efficiency
 * multiplier, Second Smelt chance, and Understanding the Art vanilla-XP multiplier. (Flux Mining is
 * commented out in legacy and stays out here.)
 */
public final class SmeltingStatsRenderer extends SkillStatsRenderer {

    private boolean canFuelEfficiency;
    private boolean canSecondSmelt;
    private String burnTimeModifier;
    private String secondSmeltChance;

    public SmeltingStatsRenderer() {
        super(PrimarySkillType.SMELTING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canFuelEfficiency = hasUnlocked(SubSkillType.SMELTING_FUEL_EFFICIENCY);
        canSecondSmelt = hasUnlocked(SubSkillType.SMELTING_SECOND_SMELT);

        if (canFuelEfficiency) {
            burnTimeModifier =
                    String.valueOf(mmoPlayer.getSmeltingManager().getFuelEfficiencyMultiplier());
        }
        if (canSecondSmelt) {
            secondSmeltChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.SMELTING_SECOND_SMELT)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canFuelEfficiency) {
            messages.add(getStatMessage(false, true, SubSkillType.SMELTING_FUEL_EFFICIENCY,
                    burnTimeModifier));
        }
        if (canSecondSmelt) {
            messages.add(getStatMessage(SubSkillType.SMELTING_SECOND_SMELT, secondSmeltChance));
        }
        if (hasUnlocked(SubSkillType.SMELTING_UNDERSTANDING_THE_ART)) {
            messages.add(getStatMessage(false, true, SubSkillType.SMELTING_UNDERSTANDING_THE_ART,
                    String.valueOf(mmoPlayer.getSmeltingManager().getVanillaXpMultiplier())));
        }

        return messages;
    }
}
