package com.gmail.nossr50.skills.herbalism;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Herbalism skill manager (Phase 10.3 port). The rank/config-driven XP math, the tall-plant XP
 * cap, the Green Thumb replant age decision, and the Green Thumb/Shroom Thumb/double-drop
 * <i>eligibility</i>+RNG gates survive; every body that reads or mutates a live block, held item,
 * inventory or Bukkit event is deferred until the block-break/held-item/item-spawn/scheduler
 * adapters land (same convention as {@link com.gmail.nossr50.skills.mining.MiningManager} and
 * {@link com.gmail.nossr50.skills.woodcutting.WoodcuttingManager}).
 *
 * <p><b>Deferred until the block-break / held-item / inventory / item-spawn / scheduler adapters
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code canGreenThumbBlock} / {@code canUseShroomThumb} — need the held-{@code ItemStack}
 *       adapter, {@code BlockUtils.canMakeMossy}/{@code canMakeShroomy}, and new
 *       {@code Permissions.greenThumbBlock} nodes;</li>
 *   <li>{@code processBerryBushHarvesting} live wiring — the age→multiplier→XP math is ported as
 *       {@link #getBerryBushXpReward(String, int)}; the live block re-check a tick later needs the
 *       Phase 11 scheduler;</li>
 *   <li>{@code farmersDiet} — needs {@code SkillUtils.handleFoodSkills} (unported) and a food-event
 *       adapter;</li>
 *   <li>{@code processGreenTerraBlockConversion} / {@code processGreenThumbBlocks} /
 *       {@code processShroomThumb} block mutation — the RNG gates
 *       ({@link #rollGreenThumbBlockSuccess()}, {@link #rollShroomThumbSuccess()}) and the
 *       conversion-target lookups ({@link Herbalism#greenTerraConversionTarget}/
 *       {@link Herbalism#shroomThumbConversionTarget}) are ported; applying the target and
 *       consuming inventory items needs the block/inventory adapters;</li>
 *   <li>{@code processHerbalismBlockBreakEvent} / {@code processHerbalismOnBlocksBroken} /
 *       {@code getBrokenHerbalismBlocks} / the chorus-tree and cactus multi-block traversal — needs
 *       live {@code Block.getRelative}, the (unported) block-tracker/{@code MaterialMapStore}, and
 *       the Phase 11 scheduler for delayed chorus XP;</li>
 *   <li>{@code checkDoubleDropsOnBrokenPlants} / {@code markForBonusDrops} — the single-block
 *       double/triple-drop path is now <b>wired</b> via {@link #isBonusDropsEligible(String)} +
 *       {@link #rollBonusDropCount()} (spawned by
 *       {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener}, same re-roll model as Mining);
 *       still deferred are the multi-block traversal ({@code getBrokenHerbalismBlocks}, one break
 *       event = one block here) and the ageable-maturity gate (see
 *       {@link #isBonusDropsEligible(String)});</li>
 *   <li>{@code awardXPForPlantBlocks} — the tall-plant XP cap math is ported as
 *       {@link #applyTallPlantXpCap(int, int, String)}; the live block-tracker iteration is not;</li>
 *   <li>{@code awardXPForBlockSnapshots} (chorus-tree delayed XP) — needs the unported
 *       {@code BlockSnapshot} datatype and block tracker;</li>
 *   <li>{@code processHylianLuck} — <b>doubly deferred</b>: needs the block-mutation/item-spawn
 *       adapters <i>and</i> {@code TreasureConfig.hylianMap}, which is currently always empty (no
 *       block-tag adapter for {@code Drops_From} groups — see
 *       {@link com.gmail.nossr50.config.treasure.TreasureConfig}); only the rank/permission gate
 *       ({@link #canUseHylianLuck()}) is ported;</li>
 *   <li>{@code processGreenThumbPlants} / {@code startReplantTask} — held-item (hoe/axe) checks,
 *       inventory consumption, and the delayed-replant scheduler task are not ported; the pure
 *       age-decision math is {@link #resolveGreenThumbReplant(String, boolean, boolean)}.</li>
 * </ul>
 */
public class HerbalismManager extends SkillManager {

    private static final String SWEET_BERRY_BUSH = "sweet_berry_bush";

    /**
     * Multi-block plants whose broken-block count can wildly exceed a single natural specimen
     * (built by a player, farmed vertically, etc.); XP for breaking one is capped at
     * {@code limit * firstBlockXp} when {@code ExploitFix.LimitTallPlantFarming} is on. Mirrors
     * legacy {@code plantBreakLimits} verbatim.
     */
    private static final Map<String, Integer> PLANT_BREAK_LIMITS = Map.of(
            "cactus", 3,
            "bamboo", 20,
            "sugar_cane", 3,
            "kelp", 26,
            "kelp_plant", 26,
            "chorus_plant", 22);

    /**
     * Ageables whose {@code age} can't be trusted for Herbalism maturity/XP purposes (they grow
     * unnaturally tall/long). Mirrors legacy {@code isBizarreAgeable}.
     */
    private static final Set<String> BIZARRE_AGEABLES = Set.of("cactus", "kelp", "sugar_cane",
            "bamboo");

    public HerbalismManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.HERBALISM);
    }

    /**
     * Rank/permission gate for Hylian Luck. The actual roll is deferred — see the class javadoc —
     * so this only ever guards a no-op today, but it's ported now so the eventual roll can drop in
     * without re-deriving the gate.
     */
    public boolean canUseHylianLuck() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HERBALISM_HYLIAN_LUCK)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_HYLIAN_LUCK);
    }

    /**
     * Whether the player is holding a raised hoe and permitted to activate Green Terra.
     */
    public boolean canActivateAbility() {
        return mmoPlayer.getToolPreparationMode(ToolType.HOE) && Permissions.greenTerra(
                getPlayer());
    }

    public boolean isGreenTerraActive() {
        return mmoPlayer.getAbilityMode(SuperAbilityType.GREEN_TERRA);
    }

    /**
     * Deterministic eligibility gate for Herbalism double drops (rank + permission), independent of
     * the RNG roll.
     */
    public boolean canDoubleDrop() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HERBALISM_DOUBLE_DROPS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_DOUBLE_DROPS);
    }

    /**
     * Whether breaking a block is eligible for Herbalism bonus drops. Deterministic gate mirroring
     * legacy {@code checkDoubleDropsOnBrokenPlants} + {@code BlockUtils.checkDoubleDrops}: the
     * subskill must be permission-enabled, the material must be listed under
     * {@code Bonus_Drops.Herbalism} in {@code config.yml}, and the double-drops rank must be
     * unlocked. The actual RNG roll is {@link #rollBonusDropCount()}. Same convention as
     * {@link com.gmail.nossr50.skills.mining.MiningManager#isBonusDropsEligible(String, boolean)};
     * there is no Silk Touch gate here because Herbalism plants don't drop via Silk Touch.
     *
     * <p>PORT (deferred, needs the live-{@code Ageable} adapter): legacy additionally requires an
     * ageable crop to be <i>mature</i> (or a bizarre ageable) before it double-drops — the maturity
     * math is ported MC-free as {@link #isAgeableMature(String, int, int)} /
     * {@link #isBizarreAgeable(String)}, but reading a block's live age generically is the same
     * gap that defers the age-based XP path, so immature crops currently pass this gate (their
     * age-appropriate loot is still what {@code BlockDrops} re-rolls). Reinstate the maturity check
     * in the caller once age reads exist.
     *
     * @param blockRegistryId the broken block's vanilla registry id (namespaced or bare)
     * @return whether a bonus-drop roll should be attempted for this block
     */
    public boolean isBonusDropsEligible(@NotNull String blockRegistryId) {
        if (!Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_DOUBLE_DROPS)) {
            return false;
        }
        final String materialConfigString =
                ConfigStringUtils.getMaterialConfigString(blockRegistryId);
        if (!McMMOMod.getGeneralConfig()
                .getDoubleDropsEnabled(PrimarySkillType.HERBALISM, materialConfigString)) {
            return false;
        }
        return canDoubleDrop();
    }

    /**
     * Rolls the number of <i>extra</i> copies of the broken plant's drops to spawn (0 or 1, or 2
     * while Green Terra is active), assuming {@link #isBonusDropsEligible(String)} already passed.
     * Mirrors legacy {@code markForBonusDrops}: a successful double-drop roll yields one extra copy,
     * or two ("triple drops") when the Green Terra super ability is active.
     *
     * @return the number of extra drop rounds to spawn (0 = the roll failed)
     */
    public int rollBonusDropCount() {
        if (!ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HERBALISM_DOUBLE_DROPS, mmoPlayer)) {
            return 0;
        }
        return isGreenTerraActive() ? 2 : 1;
    }

    /**
     * Green Thumb block-conversion RNG gate (independent of the {@link Herbalism} lookup that
     * decides <i>what</i> a block converts into).
     */
    public boolean rollGreenThumbBlockSuccess() {
        return ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HERBALISM_GREEN_THUMB, mmoPlayer);
    }

    /**
     * Shroom Thumb block-conversion RNG gate.
     */
    public boolean rollShroomThumbSuccess() {
        return ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HERBALISM_SHROOM_THUMB,
                mmoPlayer);
    }

    /**
     * Whether a block's {@code Ageable} age can't be trusted for maturity/XP purposes.
     *
     * @param blockRegistryPath the block's vanilla registry id (namespaced or bare)
     */
    public boolean isBizarreAgeable(@NotNull String blockRegistryPath) {
        return BIZARRE_AGEABLES.contains(stripToPath(blockRegistryPath));
    }

    /**
     * Whether an {@code Ageable} block is fully mature. Sweet Berry Bush is harvestable at age 2
     * and 3 (of a max age 3); every other ageable is mature only at its maximum, non-zero age.
     *
     * @param blockRegistryPath the block's vanilla registry id (namespaced or bare)
     * @param age the block's current age
     * @param maximumAge the block's maximum age
     */
    public boolean isAgeableMature(@NotNull String blockRegistryPath, int age, int maximumAge) {
        if (stripToPath(blockRegistryPath).equals(SWEET_BERRY_BUSH)) {
            return age >= 2;
        }
        return age == maximumAge && age != 0;
    }

    /**
     * Sweet Berry Bush harvest XP, mirroring legacy {@code processBerryBushHarvesting}: age 2 gives
     * normal XP, age 3 gives double, anything else gives none.
     *
     * @param blockRegistryPath the broken block's vanilla registry id (namespaced or bare)
     * @param age the bush's age at break time
     * @return the XP reward, or 0 if this isn't a sweet berry bush or it's not old enough
     */
    public int getBerryBushXpReward(@NotNull String blockRegistryPath, int age) {
        if (!stripToPath(blockRegistryPath).equals(SWEET_BERRY_BUSH)) {
            return 0;
        }
        final int multiplier = switch (age) {
            case 2 -> 1;
            case 3 -> 2;
            default -> 0;
        };
        if (multiplier == 0) {
            return 0;
        }
        return getExperienceFromPlant(ConfigStringUtils.getMaterialConfigString(SWEET_BERRY_BUSH))
                * multiplier;
    }

    /**
     * Retrieves the experience reward for a single plant block.
     *
     * @param materialConfigString the plant's config-material string (e.g. {@code "Wheat"})
     * @return amount of experience (0 if the block gives no Herbalism XP)
     */
    public static int getExperienceFromPlant(@NotNull String materialConfigString) {
        return McMMOMod.getExperienceConfig().getXp(PrimarySkillType.HERBALISM,
                materialConfigString);
    }

    /**
     * Caps XP awarded for breaking a (possibly unnaturally tall/long) multi-block plant, mirroring
     * legacy {@code awardXPForPlantBlocks}'s tall-plant guard: when
     * {@code ExploitFix.LimitTallPlantFarming} is on and the first broken block's type is a
     * configured tall-plant, total XP is capped at {@code limit * firstBlockXp}.
     *
     * @param xpToReward the summed XP across every broken block
     * @param firstBlockXp the XP a single one of those blocks would give
     * @param blockRegistryPath the first broken block's vanilla registry id (namespaced or bare)
     * @return the (possibly capped) XP to award
     */
    public int applyTallPlantXpCap(int xpToReward, int firstBlockXp,
            @NotNull String blockRegistryPath) {
        if (!McMMOMod.getExperienceConfig().limitXPOnTallPlants()) {
            return xpToReward;
        }
        final Integer limit = PLANT_BREAK_LIMITS.get(stripToPath(blockRegistryPath));
        if (limit == null) {
            return xpToReward;
        }
        return Math.min(xpToReward, limit * firstBlockXp);
    }

    /**
     * The Green Thumb replant-age decision for a broken crop, mirroring legacy
     * {@code processGrowingPlants}. Immature crops always restart at age 0; mature crops resolve a
     * per-crop-type target age from the player's Green Thumb stage (boosted by one, capped at the
     * subskill's max rank, while Green Terra is active).
     *
     * @param cropRegistryPath the crop's vanilla registry id (namespaced or bare)
     * @param isMature whether the crop was fully mature when broken
     * @param greenTerraActive whether Green Terra is currently active
     * @return the replant decision, or empty if this crop type doesn't replant (including bizarre
     *     ageables, which this never applies to)
     */
    public Optional<GreenThumbReplant> resolveGreenThumbReplant(@NotNull String cropRegistryPath,
            boolean isMature, boolean greenTerraActive) {
        final String path = stripToPath(cropRegistryPath);
        if (isBizarreAgeable(path)) {
            return Optional.empty();
        }
        if (!isMature) {
            return Optional.of(new GreenThumbReplant(0, true));
        }

        final int greenThumbStage = getGreenThumbStage(greenTerraActive);
        final int finalAge;
        switch (path) {
            case "potatoes", "carrots", "wheat" -> finalAge = greenThumbStage;
            case "beetroots", "nether_wart" -> finalAge =
                    greenTerraActive || greenThumbStage > 2 ? 2 : greenThumbStage == 2 ? 1 : 0;
            case "cocoa" -> finalAge = greenThumbStage >= 2 ? 1 : 0;
            case "sweet_berry_bush" -> finalAge = greenTerraActive || greenThumbStage >= 2 ? 1 : 0;
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(new GreenThumbReplant(finalAge, false));
    }

    /**
     * The replant material a crop's held-item/inventory check needs, mirroring legacy
     * {@code processGreenThumbPlants}'s crop→seed switch.
     *
     * @param cropRegistryPath the crop's vanilla registry id (namespaced or bare)
     * @return the seed/replant material's registry path, or empty if this crop doesn't replant
     */
    public static Optional<String> getGreenThumbReplantMaterial(
            @NotNull String cropRegistryPath) {
        return switch (stripToPath(cropRegistryPath)) {
            case "carrots" -> Optional.of("carrot");
            case "wheat" -> Optional.of("wheat_seeds");
            case "nether_wart" -> Optional.of("nether_wart");
            case "potatoes" -> Optional.of("potato");
            case "beetroots" -> Optional.of("beetroot_seeds");
            case "cocoa" -> Optional.of("cocoa_beans");
            case "torchflower" -> Optional.of("torchflower_seeds");
            case "sweet_berry_bush" -> Optional.of("sweet_berries");
            default -> Optional.empty();
        };
    }

    /**
     * The Green Thumb "stage" a player has reached: their Green Thumb rank, boosted by one (capped
     * at the subskill's highest rank) while Green Terra is active. Mirrors legacy
     * {@code getGreenThumbStage} verbatim.
     */
    int getGreenThumbStage(boolean greenTerraActive) {
        if (greenTerraActive) {
            return Math.min(RankUtils.getHighestRank(SubSkillType.HERBALISM_GREEN_THUMB),
                    RankUtils.getRank(getPlayer(), SubSkillType.HERBALISM_GREEN_THUMB) + 1);
        }
        return RankUtils.getRank(getPlayer(), SubSkillType.HERBALISM_GREEN_THUMB);
    }

    private static @NotNull String stripToPath(@NotNull String registryId) {
        final int colon = registryId.indexOf(':');
        return (colon >= 0 ? registryId.substring(colon + 1) : registryId)
                .toLowerCase(Locale.ENGLISH);
    }

    /**
     * The Green Thumb replant decision for a broken crop: the age to replant at, and whether the
     * crop was immature (which also suppresses the block's normal drops in legacy).
     *
     * @param finalAge the age to set the replanted crop to
     * @param isImmature whether the broken crop was immature (drops should be suppressed)
     */
    public record GreenThumbReplant(int finalAge, boolean isImmature) {}
}
