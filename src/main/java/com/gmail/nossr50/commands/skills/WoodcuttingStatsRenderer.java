package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats woodcutting} — port of legacy {@code WoodcuttingCommand}. Shows Harvest Lumber
 * (double drop) and Clean Cuts (triple drop) chances, Knock on Wood loot, Leaf Blower, and Tree
 * Feller duration.
 */
public final class WoodcuttingStatsRenderer extends SkillStatsRenderer {

    private boolean canDoubleDrop;
    private boolean canTripleDrop;
    private boolean canTreeFell;

    private String doubleDropChance;
    private String tripleDropChance;
    private String treeFellerLength;

    public WoodcuttingStatsRenderer() {
        super(PrimarySkillType.WOODCUTTING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        final boolean dropsDisabled = McMMOMod.getGeneralConfig().getDoubleDropsDisabled(skill);
        canDoubleDrop = !dropsDisabled && hasUnlocked(SubSkillType.WOODCUTTING_HARVEST_LUMBER);
        canTripleDrop = !dropsDisabled && hasUnlocked(SubSkillType.WOODCUTTING_CLEAN_CUTS);
        canTreeFell = hasUnlocked(SubSkillType.WOODCUTTING_TREE_FELLER);

        if (canDoubleDrop) {
            doubleDropChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.WOODCUTTING_HARVEST_LUMBER)[0];
        }
        if (canTripleDrop) {
            tripleDropChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.WOODCUTTING_CLEAN_CUTS)[0];
        }
        if (canTreeFell) {
            treeFellerLength = calculateLength(skillValue);
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canDoubleDrop) {
            messages.add(getStatMessage(SubSkillType.WOODCUTTING_HARVEST_LUMBER, doubleDropChance));
        }
        if (canTripleDrop) {
            messages.add(getStatMessage(SubSkillType.WOODCUTTING_CLEAN_CUTS, tripleDropChance));
        }
        if (canTreeFell && hasUnlocked(SubSkillType.WOODCUTTING_KNOCK_ON_WOOD)) {
            final String lootNote = RankUtils.hasReachedRank(2, mmoPlayer,
                    SubSkillType.WOODCUTTING_KNOCK_ON_WOOD)
                    ? LocaleLoader.getString("Woodcutting.SubSkill.KnockOnWood.Loot.Rank2")
                    : LocaleLoader.getString("Woodcutting.SubSkill.KnockOnWood.Loot.Normal");
            messages.add(getStatMessage(SubSkillType.WOODCUTTING_KNOCK_ON_WOOD, lootNote));
        }
        if (hasUnlocked(SubSkillType.WOODCUTTING_LEAF_BLOWER)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Woodcutting.Ability.0"),
                    LocaleLoader.getString("Woodcutting.Ability.1")));
        }
        if (canTreeFell) {
            messages.add(getStatMessage(SubSkillType.WOODCUTTING_TREE_FELLER, treeFellerLength));
        }

        return messages;
    }
}
