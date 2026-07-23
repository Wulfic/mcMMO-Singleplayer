package com.gmail.nossr50.skills.herbalism;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.datatypes.treasure.HylianTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoublePredicate;
import org.jetbrains.annotations.NotNull;

/**
 * Herbalism skill manager (Phase 10.3 port). The rank/config-driven XP math, the tall-plant XP
 * cap, the Green Thumb replant age decision, and the Green Thumb/Shroom Thumb/double-drop
 * <i>eligibility</i>+RNG gates survive; every body that reads or mutates a live block, held item,
 * inventory or Bukkit event is deferred until the block-break/held-item/item-spawn/scheduler
 * adapters land (same convention as {@link com.gmail.nossr50.skills.mining.MiningManager} and
 * {@link com.gmail.nossr50.skills.woodcutting.WoodcuttingManager}).
 *
 * <p><b>Farmer's Diet is wired</b> ({@link #farmersDiet(int)} + {@link #isFarmersDietFood(String)},
 * driven from {@code fabric.listeners.FoodListener}).
 *
 * <p><b>Deferred until the block-break / held-item / inventory / item-spawn / scheduler adapters
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code processHerbalismBlockBreakEvent} / {@code processHerbalismOnBlocksBroken} /
 *       {@code getBrokenHerbalismBlocks} / the chorus-tree and cactus multi-block traversal —
 *       <b>now wired</b>: the search itself is the MC-free {@link MultiBlockPlantTraversal}, driven
 *       from {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener}, which snapshots the plant
 *       on the pre-break seam (vanilla starts removing the rest of it before the post-break seam
 *       fires) and rewards every block. {@link #isOneBlockPlant(String)} is the divert;</li>
 *   <li>{@code checkDoubleDropsOnBrokenPlants} / {@code markForBonusDrops} — <b>wired</b> via
 *       {@link #isBonusDropsEligible(String)} + {@link #rollBonusDropCount()} (spawned by
 *       {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener}, same re-roll model as Mining),
 *       for single blocks and for every block of a multi-block plant;</li>
 *   <li>{@code awardXPForPlantBlocks} — <b>wired</b>; the tall-plant XP cap math is
 *       {@link #applyTallPlantXpCap(int, int, String)};</li>
 *   <li>{@code awardXPForBlockSnapshots} (chorus-tree delayed XP) — <b>wired</b>: the listener
 *       schedules the re-check on the tick scheduler, so legacy's {@code BlockSnapshot} datatype and
 *       {@code DelayedHerbalismXPCheckTask} both collapse into it;</li>
 *   <li>{@code processHylianLuck} — <b>now wired</b>: the pure treasure-selection core is
 *       {@link #rollHylianLuck(java.util.List, boolean, java.util.function.DoublePredicate)} (both RNG
 *       draws are caller-supplied, so it is unit-tested, exactly like the Fishing treasure roll); the
 *       rank/permission gate is {@link #canUseHylianLuck()}; the sword-break trigger, the live block
 *       group classification ({@code BlockUtils.getHylianTreasureGroup}) and the drop-replacing item
 *       spawn live on {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener} (the
 *       {@code PlayerBlockBreakEvents.BEFORE} seam, since Hylian replaces the block's normal drop);</li>
 *   <li>{@code processGreenThumbPlants} / {@code startReplantTask} — <b>now wired</b>: the held-item
 *       (hoe/axe) check, the seed inventory read/consume and the delayed block re-set live on
 *       {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener}, driven by the pure age-decision
 *       {@link #resolveGreenThumbReplant(String, boolean, boolean)} + the RNG/Green-Terra gate
 *       {@link #rollGreenThumbReplant()}. The one dropped bit is immature-crop drop suppression
 *       (legacy's {@code setDropItems(false)}): the {@code PlayerBlockBreakEvents.AFTER} seam has
 *       already spawned the drops when we run — see the listener.</li>
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

    /**
     * Foods whose hunger restoration Farmer's Diet improves, by vanilla registry path. Mirrors legacy
     * {@code EntityListener#onFoodLevelChange}'s two Herbalism switch groups plus its separate
     * {@code glow_berries} arm — upstream split them by how "common" the food is and commented that the
     * groups carry 3 vs 5 ranks, but <b>both groups call {@code farmersDiet} identically</b>, so the
     * split is vestigial and collapses into one set here. Foods upstream never listed (apple, sweet
     * berries, dried kelp…) stay out on purpose.
     */
    private static final Set<String> FARMERS_DIET_FOODS = Set.of(
            "baked_potato", "beetroot", "bread", "carrot", "golden_carrot", "mushroom_stew",
            "pumpkin_pie", "cookie", "melon_slice", "poisonous_potato", "potato", "glow_berries");

    public HerbalismManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.HERBALISM);
    }

    /**
     * Whether a food improves with Farmer's Diet.
     *
     * @param itemRegistryPath the eaten item's vanilla registry path (e.g. {@code "bread"})
     * @return whether Farmer's Diet applies to it
     */
    public static boolean isFarmersDietFood(@NotNull String itemRegistryPath) {
        return FARMERS_DIET_FOODS.contains(itemRegistryPath);
    }

    /**
     * Permission gate for Farmer's Diet, mirroring the {@code isSubSkillEnabled} check legacy makes at
     * the food-event call site. There is no rank-unlock gate: rank 0 simply adds nothing.
     */
    public boolean canUseFarmersDiet() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_FARMERS_DIET);
    }

    /**
     * Farmer's Diet — extra hunger restored from farmed foods, one point per rank (legacy
     * {@code farmersDiet}).
     *
     * @param eventFoodLevel the food restoration before the bonus
     * @return the food restoration after the bonus
     */
    public int farmersDiet(int eventFoodLevel) {
        return SkillUtils.handleFoodSkills(mmoPlayer, eventFoodLevel,
                SubSkillType.HERBALISM_FARMERS_DIET);
    }

    /**
     * Rank/permission gate for Hylian Luck (legacy {@code canUseHylianLuck}). The trigger listener
     * checks this before rolling; {@code HERBALISM_HYLIAN_LUCK} declares no ranks, so the unlock is
     * always satisfied and the scaling {@link #rollHylianLuck} RNG is the real gate.
     */
    public boolean canUseHylianLuck() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HERBALISM_HYLIAN_LUCK)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_HYLIAN_LUCK);
    }

    /**
     * The MC-free treasure-selection core of legacy {@code processHylianLuck}. Both random draws are
     * supplied by the caller so the whole selection is unit-testable (the same shape as
     * {@link com.gmail.nossr50.skills.fishing.FishingManager#rollFishingTreasure}): {@code mainRollWon}
     * is the result of the {@code HERBALISM_HYLIAN_LUCK} sub-skill roll (the primary gate — legacy
     * returns early if it fails), and {@code staticRoll} evaluates a treasure's per-drop
     * {@code Drop_Chance} (legacy's {@code isStaticSkillRNGSuccessful(HERBALISM, chance)}).
     *
     * <p>Walks the candidate treasures in config order and returns the first whose {@code Drop_Level}
     * the player has reached <em>and</em> whose static chance rolls (legacy iterates most-specific
     * first and returns on the first hit). The block classification, the item spawn and the
     * block-removal live on {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener}.
     *
     * @param candidates the treasures for the broken block's Hylian group, in config order
     * @param mainRollWon whether the {@code HERBALISM_HYLIAN_LUCK} sub-skill roll succeeded
     * @param staticRoll given a treasure's {@code Drop_Chance} (0–100), whether its static roll wins
     * @return the treasure to drop, or empty if none was won
     */
    public @NotNull Optional<HylianTreasure> rollHylianLuck(@NotNull List<HylianTreasure> candidates,
            boolean mainRollWon, @NotNull DoublePredicate staticRoll) {
        if (!mainRollWon || candidates.isEmpty()) {
            return Optional.empty();
        }
        final int skillLevel = getSkillLevel();
        for (HylianTreasure treasure : candidates) {
            if (skillLevel >= treasure.getDropLevel() && staticRoll.test(treasure.getDropChance())) {
                return Optional.of(treasure);
            }
        }
        return Optional.empty();
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
     * <p>Legacy additionally requires an ageable crop to be <i>mature</i> (or a bizarre ageable)
     * before it double-drops. That check is the caller's, since it needs the block's live age: both
     * Herbalism handlers in {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener} (the
     * maturity-gated crop path and the multi-block plant path) apply it via
     * {@link #isAgeableMature(String, int, int)} / {@link #isBizarreAgeable(String)} before calling
     * this. An ageable that reaches the generic single-block gathering path is not maturity-checked,
     * but by construction that path only sees blocks {@link #isMaturityGatedCrop(String)} rejected.
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
     * The rank+enable half of legacy {@code canGreenThumbBlock} — whether the player has unlocked
     * Green Thumb and has the sub-skill enabled. The MC-typed half (a {@code wheat_seeds} main hand
     * on a {@link com.gmail.nossr50.util.BlockUtils#canMakeMossy mossify-able} block) lives on
     * {@link com.gmail.nossr50.fabric.listeners.SuperAbilityListener}, as the MC-free/MC-typed split
     * requires; the {@link #rollGreenThumbBlockSuccess()} RNG then decides whether the block converts.
     */
    public boolean canGreenThumbBlock() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HERBALISM_GREEN_THUMB)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_GREEN_THUMB);
    }

    /**
     * The rank+enable half of legacy {@code canUseShroomThumb}. The MC-typed half (a mushroom in the
     * main hand, one brown + one red mushroom in the pack, and a
     * {@link com.gmail.nossr50.util.BlockUtils#canMakeShroomy shroomy-able} block) lives on
     * {@link com.gmail.nossr50.fabric.listeners.SuperAbilityListener}. Like Hylian Luck,
     * {@code HERBALISM_SHROOM_THUMB} declares no ranks, so the unlock is always satisfied and the
     * scaling {@link #rollShroomThumbSuccess()} RNG is the real gate.
     */
    public boolean canUseShroomThumb() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HERBALISM_SHROOM_THUMB)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HERBALISM_SHROOM_THUMB);
    }

    /**
     * Whether a broken block stands alone, or is part of a multi-block plant (sugar cane, a cactus
     * column, a chorus tree, tall grass…) whose other blocks come down with it. Legacy
     * {@code isOneBlockPlant}; the traversal that finds those other blocks is
     * {@link MultiBlockPlantTraversal}.
     *
     * @param blockRegistryId the broken block's vanilla registry id (namespaced or bare)
     */
    public boolean isOneBlockPlant(@NotNull String blockRegistryId) {
        return MultiBlockPlantTraversal.isOneBlockPlant(stripToPath(blockRegistryId),
                McMMOMod.getMaterialMapStore());
    }

    /**
     * Whether Herbalism rewards are suppressed while the player is riding something —
     * {@code Skills.Herbalism.Prevent_AFK_Leveling} in {@code config.yml} (ships on). Aimed at the classic
     * "sit in a minecart next to a sugar-cane farm" setup; the caller pairs it with the live
     * {@code hasVehicle()} read (legacy {@code processHerbalismBlockBreakEvent}'s opening guard).
     */
    public boolean isHerbalismAfkPrevented() {
        return McMMOMod.getGeneralConfig().getHerbalismPreventAFK();
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
     * Whether a broken ageable block is a single-block farm crop whose Herbalism rewards (XP + bonus
     * drops) are gated on <i>maturity</i> rather than on the placed-block flag. This is the set of
     * crops legacy's {@code awardXPForPlantBlocks} / {@code checkDoubleDropsOnBrokenPlants} reward
     * only when fully grown — for a non-bizarre ageable, <b>both</b> the placed and natural branches
     * require full maturity, so a crop pays the same whether the player planted it or found it wild.
     *
     * <p>Excluded, matching legacy's non-ageable / bizarre-ageable branches:
     * <ul>
     *   <li>bizarre ageables (cactus / kelp / sugar cane / bamboo) — their {@code age} can't be
     *       trusted for maturity, and legacy rewards them off the natural/placed flag, not maturity;</li>
     *   <li>chorus — rewarded per collapsed block after a delay, not on maturity.</li>
     *   <li>any ageable that grants no Herbalism XP (a non-crop age property).</li>
     * </ul>
     *
     * <p>Every one of those exclusions is a multi-block plant, so in
     * {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener} they are already claimed by the
     * multi-block handler before this divert is consulted; anything left over falls through to the
     * ordinary placed-flag gathering path.
     *
     * @param blockRegistryId the broken block's vanilla registry id (namespaced or bare)
     * @return whether this block's Herbalism rewards should be maturity-gated
     */
    public boolean isMaturityGatedCrop(@NotNull String blockRegistryId) {
        final String path = stripToPath(blockRegistryId);
        if (isBizarreAgeable(path) || path.equals("chorus_plant") || path.equals("chorus_flower")) {
            return false;
        }
        return getExperienceFromPlant(ConfigStringUtils.getMaterialConfigString(path)) > 0;
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
     * Green Thumb replant RNG gate: succeeds automatically while Green Terra is active (legacy's
     * {@code greenTerra} bypass in {@code processGreenThumbPlants}), otherwise rolls the
     * {@code HERBALISM_GREEN_THUMB} subskill probability. Reads {@link #isGreenTerraActive()} on the
     * manager (the same pattern as {@link #rollBonusDropCount()}) so the block-break glue only owns
     * the MC-typed inventory read/consume and the block re-set.
     *
     * @return whether the harvested crop should be replanted this break
     */
    public boolean rollGreenThumbReplant() {
        return isGreenTerraActive()
                || ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HERBALISM_GREEN_THUMB,
                mmoPlayer);
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
