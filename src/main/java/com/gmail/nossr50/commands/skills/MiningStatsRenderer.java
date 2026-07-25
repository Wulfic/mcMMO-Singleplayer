package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats mining} — port of legacy {@code MiningCommand}. Shows Blast Mining (rank, ore
 * bonus, bonus TNT drops), Bigger Bombs (radius), Demolitions Expertise (blast damage reduction),
 * Double/Triple (Mother Lode) drop chances, and Super Breaker duration.
 */
public final class MiningStatsRenderer extends SkillStatsRenderer {

    private int blastMiningRank;
    private int bonusTntDrops;
    private double blastRadiusIncrease;
    private String oreBonus;
    private String blastDamageDecrease;

    private String doubleDropChance;
    private String tripleDropChance;
    private String superBreakerLength;

    public MiningStatsRenderer() {
        super(PrimarySkillType.MINING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        final MiningManager miningManager = mmoPlayer.getMiningManager();

        if (hasUnlocked(SubSkillType.MINING_BLAST_MINING)
                || hasUnlocked(SubSkillType.MINING_DEMOLITIONS_EXPERTISE)
                || hasUnlocked(SubSkillType.MINING_BIGGER_BOMBS)) {
            blastMiningRank = miningManager.getBlastMiningTier();
            bonusTntDrops = miningManager.getDropMultiplier();
            oreBonus = percent.format(miningManager.getOreBonus());
            blastDamageDecrease = percent.format(miningManager.getBlastDamageModifier() / 100.0D);
            blastRadiusIncrease = miningManager.getBlastRadiusModifier();
        }

        if (hasUnlocked(SubSkillType.MINING_MOTHER_LODE)) {
            tripleDropChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.MINING_MOTHER_LODE)[0];
        }
        if (hasUnlocked(SubSkillType.MINING_DOUBLE_DROPS)) {
            doubleDropChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.MINING_DOUBLE_DROPS)[0];
        }
        if (hasUnlocked(SubSkillType.MINING_SUPER_BREAKER)) {
            superBreakerLength = calculateLength(skillValue);
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.MINING_BIGGER_BOMBS)) {
            messages.add(getStatMessage(true, true, SubSkillType.MINING_BLAST_MINING,
                    String.valueOf(blastRadiusIncrease)));
        }
        if (hasUnlocked(SubSkillType.MINING_BLAST_MINING)) {
            messages.add(getStatMessage(false, true, SubSkillType.MINING_BLAST_MINING,
                    String.valueOf(blastMiningRank),
                    String.valueOf(RankUtils.getHighestRank(SubSkillType.MINING_BLAST_MINING)),
                    LocaleLoader.getString("Mining.Blast.Effect", oreBonus, bonusTntDrops)));
        }
        if (hasUnlocked(SubSkillType.MINING_DEMOLITIONS_EXPERTISE)) {
            messages.add(getStatMessage(SubSkillType.MINING_DEMOLITIONS_EXPERTISE,
                    blastDamageDecrease));
        }
        if (hasUnlocked(SubSkillType.MINING_DOUBLE_DROPS)) {
            messages.add(getStatMessage(SubSkillType.MINING_DOUBLE_DROPS, doubleDropChance));
        }
        if (hasUnlocked(SubSkillType.MINING_MOTHER_LODE)) {
            messages.add(getStatMessage(SubSkillType.MINING_MOTHER_LODE, tripleDropChance));
        }
        if (hasUnlocked(SubSkillType.MINING_SUPER_BREAKER)) {
            messages.add(getStatMessage(SubSkillType.MINING_SUPER_BREAKER, superBreakerLength));
        }

        return messages;
    }
}
