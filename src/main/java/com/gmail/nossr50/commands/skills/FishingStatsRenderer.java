package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.config.treasure.FishingTreasureConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.util.random.Probability;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.text.StringUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats fishing} — port of legacy {@code FishingCommand}. Shows Fisherman's Diet, Ice
 * Fishing, Magic Hunter chance, Master Angler wait-time reductions, Shake chance, and Treasure
 * Hunter (loot tier + per-rarity drop rates).
 */
public final class FishingStatsRenderer extends SkillStatsRenderer {

    private boolean canTreasureHunt;
    private boolean canMagicHunt;
    private boolean canShake;
    private boolean canFishermansDiet;
    private boolean canMasterAngler;
    private boolean canIceFish;

    private int lootTier;
    private int fishermansDietRank;
    private String shakeChance;
    private String magicChance;
    private String maMinWaitTime;
    private String maMaxWaitTime;
    private String commonTreasure;
    private String uncommonTreasure;
    private String rareTreasure;
    private String epicTreasure;
    private String legendaryTreasure;
    private String mythicTreasure;

    public FishingStatsRenderer() {
        super(PrimarySkillType.FISHING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        final FishingManager fishingManager = mmoPlayer.getFishingManager();
        final FishingTreasureConfig treasureConfig = McMMOMod.getFishingTreasureConfig();

        canTreasureHunt = hasUnlocked(SubSkillType.FISHING_TREASURE_HUNTER) && treasureConfig != null;
        canMagicHunt = canTreasureHunt && hasUnlocked(SubSkillType.FISHING_MAGIC_HUNTER);
        canShake = hasUnlocked(SubSkillType.FISHING_SHAKE);
        canFishermansDiet = hasUnlocked(SubSkillType.FISHING_FISHERMANS_DIET);
        canMasterAngler = hasUnlocked(SubSkillType.FISHING_MASTER_ANGLER);
        canIceFish = hasUnlocked(SubSkillType.FISHING_ICE_FISHING);

        if (canTreasureHunt) {
            lootTier = fishingManager.getLootTier();
            commonTreasure = percent.format(
                    treasureConfig.getItemDropRate(lootTier, Rarity.COMMON) / 100.0);
            uncommonTreasure = percent.format(
                    treasureConfig.getItemDropRate(lootTier, Rarity.UNCOMMON) / 100.0);
            rareTreasure = percent.format(
                    treasureConfig.getItemDropRate(lootTier, Rarity.RARE) / 100.0);
            epicTreasure = percent.format(
                    treasureConfig.getItemDropRate(lootTier, Rarity.EPIC) / 100.0);
            legendaryTreasure = percent.format(
                    treasureConfig.getItemDropRate(lootTier, Rarity.LEGENDARY) / 100.0);
            mythicTreasure = percent.format(
                    treasureConfig.getItemDropRate(lootTier, Rarity.MYTHIC) / 100.0);

            double totalEnchantChance = 0;
            for (Rarity rarity : Rarity.values()) {
                if (rarity != Rarity.MYTHIC) {
                    totalEnchantChance += treasureConfig.getEnchantmentDropRate(lootTier, rarity);
                }
            }
            magicChance = percent.format(totalEnchantChance >= 1 ? totalEnchantChance / 100.0 : 0);
        }
        if (canShake) {
            final Probability shakeProbability = Probability.ofPercent(fishingManager.getShakeChance());
            shakeChance = ProbabilityUtil.getRNGDisplayValues(shakeProbability)[0];
        }
        if (canFishermansDiet) {
            fishermansDietRank =
                    RankUtils.getRank(mmoPlayer, SubSkillType.FISHING_FISHERMANS_DIET);
        }
        if (canMasterAngler) {
            final int rank = RankUtils.getRank(mmoPlayer, SubSkillType.FISHING_MASTER_ANGLER);
            maMinWaitTime = StringUtils.ticksToSeconds(
                    fishingManager.getMasterAnglerTickMinWaitReduction(rank, false));
            maMaxWaitTime = StringUtils.ticksToSeconds(
                    fishingManager.getMasterAnglerTickMaxWaitReduction(rank, false, 0));
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canFishermansDiet) {
            messages.add(getStatMessage(false, true, SubSkillType.FISHING_FISHERMANS_DIET,
                    String.valueOf(fishermansDietRank)));
        }
        if (canIceFish) {
            messages.add(getStatMessage(SubSkillType.FISHING_ICE_FISHING,
                    SubSkillType.FISHING_ICE_FISHING.getLocaleStatDescription()));
        }
        if (canMagicHunt) {
            messages.add(getStatMessage(SubSkillType.FISHING_MAGIC_HUNTER, magicChance));
        }
        if (canMasterAngler) {
            messages.add(getStatMessage(false, true, SubSkillType.FISHING_MASTER_ANGLER,
                    maMinWaitTime));
            messages.add(getStatMessage(true, true, SubSkillType.FISHING_MASTER_ANGLER,
                    maMaxWaitTime));
        }
        if (canShake) {
            messages.add(getStatMessage(SubSkillType.FISHING_SHAKE, shakeChance));
        }
        if (canTreasureHunt) {
            messages.add(getStatMessage(false, true, SubSkillType.FISHING_TREASURE_HUNTER,
                    String.valueOf(lootTier),
                    String.valueOf(RankUtils.getHighestRank(SubSkillType.FISHING_TREASURE_HUNTER))));
            messages.add(getStatMessage(true, true, SubSkillType.FISHING_TREASURE_HUNTER,
                    commonTreasure, uncommonTreasure, rareTreasure, epicTreasure,
                    legendaryTreasure, mythicTreasure));
        }

        return messages;
    }
}
