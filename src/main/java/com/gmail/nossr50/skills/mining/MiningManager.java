package com.gmail.nossr50.skills.mining;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Mining skill manager (Phase 10.3 port). The rank/config-driven Blast Mining math and the
 * double/triple-drop <i>eligibility</i> gates survive; every body that reads or mutates a live
 * block, item, TNT entity or Bukkit event is deferred until the block-break + item-spawn adapters
 * land (same convention as {@link com.gmail.nossr50.skills.excavation.ExcavationManager}).
 *
 * <p><b>Deferred until the block-break / item-spawn / scheduler adapters (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code miningBlockCheck} / {@code processDoubleDrops} / {@code processTripleDrops} — award
 *       block XP then mark the broken block's drops as bonus via {@code BlockUtils.markDropsAsBonus}
 *       (needs a live block + the drop-marking adapter) and damage the held tool during Super
 *       Breaker ({@code SkillUtils.handleDurabilityChange});</li>
 *   <li>{@code canDetonate} / {@code isDetonatorInHand} / {@code remoteDetonation} — ray-cast the
 *       target block, spawn a primed {@code TNTPrimed}, and schedule the cooldown task (needs the
 *       held-item + entity-spawn adapters and the Phase 11 scheduler);</li>
 *   <li>{@code blastMiningDropProcessing} — consumes a Bukkit {@code EntityExplodeEvent} and spawns
 *       ore/debris {@code ItemStack}s (needs the explosion hook + item-spawn adapter).</li>
 * </ul>
 * The numeric helpers those bodies call ({@link #getOreBonus()}, {@link #getDropMultiplier()},
 * {@link #biggerBombs(float)}, {@link #processDemolitionsExpertise(double)}, …) are ported here so
 * the bodies drop in cleanly once the adapters exist.
 */
public class MiningManager extends SkillManager {

    private static final String BUDDING_AMETHYST = "budding_amethyst";
    private static final Set<String> INFESTED_BLOCKS = Set.of("infested_stone",
            "infested_cobblestone",
            "infested_stone_bricks", "infested_cracked_stone_bricks", "infested_mossy_stone_bricks",
            "infested_chiseled_stone_bricks", "infested_deepslate");

    public MiningManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.MINING);
    }

    public boolean canUseDemolitionsExpertise() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.MINING_DEMOLITIONS_EXPERTISE)) {
            return false;
        }

        return getSkillLevel() >= BlastMining.getDemolitionExpertUnlockLevel()
                && Permissions.demolitionsExpertise(getPlayer());
    }

    public boolean canUseBlastMining() {
        //Not checking permissions?
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.MINING_BLAST_MINING);
    }

    public boolean canUseBiggerBombs() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.MINING_BIGGER_BOMBS)) {
            return false;
        }

        return getSkillLevel() >= BlastMining.getBiggerBombsUnlockLevel()
                && Permissions.biggerBombs(getPlayer());
    }

    public boolean canDoubleDrop() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.MINING_DOUBLE_DROPS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.MINING_DOUBLE_DROPS);
    }

    public boolean canMotherLode() {
        return Permissions.canUseSubSkill(getPlayer(), SubSkillType.MINING_MOTHER_LODE);
    }

    private boolean isInfestedBlock(@NotNull String blockRegistryPath) {
        return INFESTED_BLOCKS.contains(blockRegistryPath.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Checks if it would be illegal (in vanilla) to obtain the block. Certain things should never
     * drop (such as budding_amethyst, infested blocks or spawners).
     *
     * @param blockRegistryPath the block's vanilla registry path (e.g. {@code "spawner"})
     * @return true if it's not legal to get the block through normal gameplay
     */
    public boolean isDropIllegal(@NotNull String blockRegistryPath) {
        final String path = blockRegistryPath.toLowerCase(Locale.ENGLISH);
        return isInfestedBlock(path)
                || path.equals(BUDDING_AMETHYST)
                || path.equals("spawner");
    }

    /**
     * Increases the blast radius of the explosion.
     *
     * @param radius to modify
     * @return modified radius
     */
    public float biggerBombs(float radius) {
        return (float) (radius + getBlastRadiusModifier());
    }

    public double processDemolitionsExpertise(double damage) {
        return damage * ((100.0D - getBlastDamageModifier()) / 100.0D);
    }

    /**
     * Gets the Blast Mining tier.
     *
     * @return the Blast Mining tier
     */
    public int getBlastMiningTier() {
        return RankUtils.getRank(getPlayer(), SubSkillType.MINING_BLAST_MINING);
    }

    public float getOreBonus() {
        return (float) (McMMOMod.getAdvancedConfig().getOreBonus(getBlastMiningTier()) / 100F);
    }

    public static double getOreBonus(int rank) {
        return McMMOMod.getAdvancedConfig().getOreBonus(rank);
    }

    public static double getDebrisReduction(int rank) {
        return McMMOMod.getAdvancedConfig().getDebrisReduction(rank);
    }

    public double getDebrisReduction() {
        return getDebrisReduction(getBlastMiningTier());
    }

    public static int getDropMultiplier(int rank) {
        return McMMOMod.getAdvancedConfig().getDropMultiplier(rank);
    }

    public int getDropMultiplier() {
        if (!McMMOMod.getAdvancedConfig().isBlastMiningBonusDropsEnabled()) {
            return 0;
        }

        return switch (getBlastMiningTier()) {
            case 8, 7 -> 3;
            case 6, 5, 4, 3 -> 2;
            case 2, 1 -> 1;
            default -> 0;
        };
    }

    public double getBlastRadiusModifier() {
        return BlastMining.getBlastRadiusModifier(getBlastMiningTier());
    }

    public double getBlastDamageModifier() {
        return BlastMining.getBlastDamageDecrease(getBlastMiningTier());
    }
}
