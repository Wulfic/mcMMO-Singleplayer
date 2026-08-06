package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats herbalism} — port of legacy {@code HerbalismCommand}. Shows Double/Triple (Verdant
 * Bounty) drop chances, Farmer's Diet rank, Green Terra duration, Green Thumb (chance + stage),
 * Hylian Luck, and Shroom Thumb.
 *
 * <p>⚠️ <b>The Triple Drop line reports the Double Drop probability on purpose.</b> Verdant Bounty
 * is not a rank-gated roll of its own — unlike its two siblings, Mining's Mother Lode and
 * Woodcutting's Clean Cuts, which are. It is a rider on Green Terra:
 * {@code HerbalismManager#rollBonusDropCount} rolls at the <b>{@code HERBALISM_DOUBLE_DROPS}</b>
 * probability and returns two extra drops instead of one whenever the Green Terra super ability is
 * active. That is legacy's behaviour ({@code awardTriple = getAbilityMode(GREEN_TERRA)}) and it is
 * kept.
 *
 * <p>What was <em>not</em> kept is this line quoting {@code Skills.Herbalism.VerdantBounty.ChanceMax}
 * — a number no gameplay code has ever consulted, read off a different rank and a different config
 * key than the mechanic it claimed to describe (TODO.md item 1.2). The knob is retired; the label
 * now names the Green Terra condition that actually governs the drop.
 */
public final class HerbalismStatsRenderer extends SkillStatsRenderer {

    private boolean canDoubleDrop;
    private boolean canTripleDrop;
    private boolean canGreenTerra;
    private boolean canGreenThumb;

    private String doubleDropChance;
    private String tripleDropChance;
    private int farmersDietRank;
    private String greenTerraLength;
    private String greenThumbChance;
    private int greenThumbStage;
    private String hylianLuckChance;
    private String shroomThumbChance;

    public HerbalismStatsRenderer() {
        super(PrimarySkillType.HERBALISM);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        final boolean dropsDisabled = McMMOMod.getGeneralConfig().getDoubleDropsDisabled(skill);
        canDoubleDrop = !dropsDisabled && hasUnlocked(SubSkillType.HERBALISM_DOUBLE_DROPS);
        canTripleDrop = !dropsDisabled && hasUnlocked(SubSkillType.HERBALISM_VERDANT_BOUNTY);
        canGreenTerra = hasUnlocked(SubSkillType.HERBALISM_GREEN_TERRA);
        canGreenThumb = hasUnlocked(SubSkillType.HERBALISM_GREEN_THUMB);

        if (canDoubleDrop) {
            doubleDropChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.HERBALISM_DOUBLE_DROPS)[0];
        }
        if (canTripleDrop) {
            // Deliberately the DOUBLE DROPS probability: that is the roll the triple actually makes.
            // See the class javadoc — quoting VERDANT_BOUNTY's own chance here was the defect.
            tripleDropChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.HERBALISM_DOUBLE_DROPS)[0];
        }
        if (hasUnlocked(SubSkillType.HERBALISM_FARMERS_DIET)) {
            farmersDietRank = RankUtils.getRank(mmoPlayer, SubSkillType.HERBALISM_FARMERS_DIET);
        }
        if (canGreenTerra) {
            greenTerraLength = calculateLength(skillValue);
        }
        if (canGreenThumb) {
            greenThumbStage = RankUtils.getRank(mmoPlayer, SubSkillType.HERBALISM_GREEN_THUMB);
            greenThumbChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.HERBALISM_GREEN_THUMB)[0];
        }
        if (hasUnlocked(SubSkillType.HERBALISM_HYLIAN_LUCK)) {
            hylianLuckChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.HERBALISM_HYLIAN_LUCK)[0];
        }
        if (hasUnlocked(SubSkillType.HERBALISM_SHROOM_THUMB)) {
            shroomThumbChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.HERBALISM_SHROOM_THUMB)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canDoubleDrop) {
            messages.add(getStatMessage(SubSkillType.HERBALISM_DOUBLE_DROPS, doubleDropChance));
        }
        if (canTripleDrop) {
            messages.add(getStatMessage(SubSkillType.HERBALISM_VERDANT_BOUNTY, tripleDropChance));
        }
        if (hasUnlocked(SubSkillType.HERBALISM_FARMERS_DIET)) {
            messages.add(getStatMessage(false, true, SubSkillType.HERBALISM_FARMERS_DIET,
                    String.valueOf(farmersDietRank)));
        }
        if (canGreenTerra) {
            messages.add(getStatMessage(SubSkillType.HERBALISM_GREEN_TERRA, greenTerraLength));
        }
        if (canGreenThumb) {
            messages.add(getStatMessage(SubSkillType.HERBALISM_GREEN_THUMB, greenThumbChance));
            messages.add(getStatMessage(true, true, SubSkillType.HERBALISM_GREEN_THUMB,
                    String.valueOf(greenThumbStage)));
        }
        if (hasUnlocked(SubSkillType.HERBALISM_HYLIAN_LUCK)) {
            messages.add(getStatMessage(SubSkillType.HERBALISM_HYLIAN_LUCK, hylianLuckChance));
        }
        if (hasUnlocked(SubSkillType.HERBALISM_SHROOM_THUMB)) {
            messages.add(getStatMessage(SubSkillType.HERBALISM_SHROOM_THUMB, shroomThumbChance));
        }

        return messages;
    }
}
