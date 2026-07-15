package com.gmail.nossr50.skills.woodcutting;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Woodcutting skill manager (Phase 10.6 port). The XP-per-log lookup, the Tree Feller XP-reduction
 * curve, and the Harvest Lumber / Clean Cuts bonus-drop <i>activation</i> gates survive; every body
 * that reads or mutates a live block, held tool, dropped item or experience orb is deferred until
 * the block-break + item-spawn adapters land (same convention as
 * {@link com.gmail.nossr50.skills.mining.MiningManager} and
 * {@link com.gmail.nossr50.skills.excavation.ExcavationManager}).
 *
 * <p><b>Deferred until the block-break / held-item / item-spawn / scheduler adapters
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code processWoodcuttingBlockXP} — awards {@link #getExperienceFromLog(String)} for a single
 *       broken log (needs a live block + the block-tracker eligibility adapter);</li>
 *   <li>{@code processBonusDropCheck} / {@code spawnHarvestLumberBonusDrops} — roll
 *       {@link #checkHarvestLumberActivation(String)} / {@link #checkCleanCutsActivation(String)} then
 *       re-spawn the block's drops (needs the item-spawn adapter);</li>
 *   <li>{@code processTreeFeller} / {@code processTree} / {@code processTreeFellerTargetBlock} /
 *       {@code dropTreeFellerLootFromBlocks} / {@code handleDurabilityLoss} — the recursive tree
 *       search, the durability drain + {@code PlayerItemDamageEvent}, and the per-log drop/XP-orb
 *       loop (needs live blocks, {@code BlockUtils.hasWoodcuttingXP}, tool durability, item-spawn and
 *       the Knock on Wood sapling filter).</li>
 * </ul>
 * The numeric helpers those bodies call ({@link #getExperienceFromLog(String)},
 * {@link #processTreeFellerXPGains(String, int)}, the two activation gates) are ported here so the
 * bodies drop in cleanly once the adapters exist.
 */
public class WoodcuttingManager extends SkillManager {

    public static final String SAPLING = "sapling";
    public static final String PROPAGULE = "propagule";

    /**
     * Tree Feller propagation cap, read once at construction from the general config (legacy parity).
     * Consumed by the deferred {@code processTree} recursion.
     */
    private final int treeFellerThreshold;

    public WoodcuttingManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.WOODCUTTING);
        this.treeFellerThreshold = McMMOMod.getGeneralConfig().getTreeFellerThreshold();
    }

    /**
     * @return the Tree Feller block-propagation threshold captured at construction.
     */
    public int getTreeFellerThreshold() {
        return treeFellerThreshold;
    }

    /**
     * Whether the player may use Leaf Blower — the sub-skill that lets an axe insta-break the non-wood
     * parts of a tree (leaves, mushroom caps, warts) instead of chewing through them.
     *
     * <p>Ports legacy {@code canUseLeafBlower(ItemStack)} minus its {@code ItemUtils.isAxe(heldItem)}
     * half: the held-item classification is MC-typed, so it stays on the caller
     * ({@code SuperAbilityListener}), matching how {@code UnarmedManager#canUseBlockCracker} splits.
     */
    public boolean canUseLeafBlower() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.WOODCUTTING_LEAF_BLOWER)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.WOODCUTTING_LEAF_BLOWER);
    }

    /**
     * Whether a Harvest Lumber bonus drop should trigger for the given block.
     *
     * @param materialConfigString the block's config-material string (e.g. {@code "Oak_Log"})
     */
    boolean checkHarvestLumberActivation(@NotNull String materialConfigString) {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.WOODCUTTING_HARVEST_LUMBER)
                && RankUtils.hasReachedRank(1, mmoPlayer, SubSkillType.WOODCUTTING_HARVEST_LUMBER)
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.WOODCUTTING_HARVEST_LUMBER,
                mmoPlayer)
                && McMMOMod.getGeneralConfig()
                .getDoubleDropsEnabled(PrimarySkillType.WOODCUTTING, materialConfigString);
    }

    /**
     * Whether a Clean Cuts (triple drop) bonus should trigger for the given block. Clean Cuts is
     * unlocked by the Harvest Lumber subskill but rolled against its own probability.
     *
     * @param materialConfigString the block's config-material string (e.g. {@code "Oak_Log"})
     */
    boolean checkCleanCutsActivation(@NotNull String materialConfigString) {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.WOODCUTTING_HARVEST_LUMBER)
                && RankUtils.hasReachedRank(1, mmoPlayer, SubSkillType.WOODCUTTING_HARVEST_LUMBER)
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.WOODCUTTING_CLEAN_CUTS,
                mmoPlayer)
                && McMMOMod.getGeneralConfig()
                .getDoubleDropsEnabled(PrimarySkillType.WOODCUTTING, materialConfigString);
    }

    /**
     * Rolls the number of <i>extra</i> copies of a broken log's drops to spawn (0, 1, or 2),
     * mirroring legacy {@code processBonusDropCheck}: Clean Cuts (the Harvest Lumber mastery) grants a
     * triple drop — 2 extra copies — on success, otherwise Harvest Lumber grants a double drop — 1
     * extra copy. A block that isn't a configured {@code Bonus_Drops.Woodcutting} material is rejected
     * up front (cheap, deterministic) before either RNG gate runs, so breaking non-logs never consumes
     * the skill RNG stream — matching the outer {@code getDoubleDropsEnabled} guard legacy checked
     * first. This is the RNG counterpart to {@code MiningManager#rollBonusDropCount}; the
     * item-spawn itself is performed by the caller via {@code platform/BlockDrops}.
     *
     * @param blockRegistryId the broken block's vanilla registry id (namespaced or bare)
     * @return the number of extra drop rounds to spawn (0 = not a bonus-drop log, subskill locked,
     *     or the roll failed)
     */
    public int rollHarvestLumberBonusDropCount(@NotNull String blockRegistryId) {
        final String materialConfigString = ConfigStringUtils.getMaterialConfigString(blockRegistryId);
        if (!McMMOMod.getGeneralConfig()
                .getDoubleDropsEnabled(PrimarySkillType.WOODCUTTING, materialConfigString)) {
            return 0;
        }
        if (checkCleanCutsActivation(materialConfigString)) {
            return 2;
        }
        if (checkHarvestLumberActivation(materialConfigString)) {
            return 1;
        }
        return 0;
    }

    /**
     * Retrieves the experience reward from a single log.
     *
     * @param materialConfigString the log's config-material string (e.g. {@code "Oak_Log"})
     * @return amount of experience (0 if the block gives no Woodcutting XP)
     */
    public static int getExperienceFromLog(@NotNull String materialConfigString) {
        return McMMOMod.getExperienceConfig()
                .getXp(PrimarySkillType.WOODCUTTING, materialConfigString);
    }

    /**
     * Retrieves the experience reward from a log broken by Tree Feller. Experience is reduced per log
     * already processed this Tree Feller, but only when the {@code TreeFellerReducedXP} exploit toggle
     * is on, and never below 1 (unless the log's raw XP is already 0).
     *
     * @param materialConfigString the log's config-material string (e.g. {@code "Oak_Log"})
     * @param woodCount how many logs have already given out XP for this Tree Feller
     * @return amount of experience
     */
    static int processTreeFellerXPGains(@NotNull String materialConfigString, int woodCount) {
        final int rawXP = getExperienceFromLog(materialConfigString);

        if (rawXP <= 0) {
            return 0;
        }

        if (McMMOMod.getExperienceConfig().isTreeFellerXPReduced()) {
            return Math.max(1, rawXP - (woodCount * 5));
        }

        return rawXP;
    }
}
