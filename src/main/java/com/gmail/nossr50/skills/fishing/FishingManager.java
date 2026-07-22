package com.gmail.nossr50.skills.fishing;

import com.gmail.nossr50.config.treasure.FishingTreasureConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.datatypes.treasure.ShakeTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import org.jetbrains.annotations.NotNull;

/**
 * Fishing skill manager (Phase 10.8 port, legacy 749 lines). The Master Angler wait-time math, the
 * Shake/Treasure-Hunter/Magic-Hunter rank+permission gates, the vanilla-XP boost multiplier, and the
 * fishing-spot exploit-detection state machine survive as pure functions; every body that touches a
 * live {@code FishHook}, {@code ItemStack}, {@code Enchantment}, {@code Entity}, vehicle, or block
 * (and the still-unported {@code FishingTreasureConfig} loot tables) is deferred until those
 * adapters land — same convention as {@link com.gmail.nossr50.skills.woodcutting.WoodcuttingManager}
 * and {@link com.gmail.nossr50.skills.herbalism.HerbalismManager}.
 *
 * <p><b>Treasure Hunter item roll is now wired</b> ({@link #rollFishingTreasure} +
 * {@link #awardFishingTreasureXP}, driven from {@code fabric.listeners.FishingListener} via
 * {@code ItemSpecBuilder}), and so is <b>Shake</b> ({@link #rollShakeSuccess} +
 * {@link #rollShakeTreasure} + {@link #shakeDamage} + {@link #awardShakeXP}, driven from the same
 * listener when a hooked mob is reeled in). Still deferred until the entity/item/block/enchant
 * adapters:</p>
 * <ul>
 *   <li>{@code processMagicHunter}/{@code getPossibleEnchantments} — the Magic Hunter enchant roll on
 *       a caught treasure needs the dynamic 1.21 enchantment registry + the K3 enchant-write surface
 *       (its {@code FishingTreasureConfig} enchant tables are deferred for the same reason);</li>
 *   <li>{@code canIceFish}/{@code iceFishing} — need the live {@code Block}/{@code Biome} adapter;</li>
 *   <li>{@code masterAngler}/{@code processMasterAngler} — need the {@code FishHook} adapter and the
 *       Folia-scheduler {@code MasterAnglerTask}; the wait-time math itself is extracted below as
 *       {@link #resolveMasterAnglerWaitTimes} so the eventual body is a thin FishHook-mutation
 *       wrapper around it (same "buried pure decision" extraction as Herbalism's
 *       {@code resolveGreenThumbReplant});</li>
 *   <li>{@code isInBoat} — needs the vehicle/{@code Boat} adapter;</li>
 *   <li>{@code handleFishermanDiet} — needs {@code SkillUtils.handleFoodSkills}, still unported.</li>
 * </ul>
 */
public class FishingManager extends SkillManager {

    /**
     * Wait-tick reduction one level of the vanilla Lure enchantment is worth (5 seconds), i.e. legacy's
     * {@code lureLevel * 100} conversion and vanilla's own {@code fishing_time_reduction} effect.
     */
    static final int LURE_TICKS_PER_LEVEL = 100;

    private long lastFishCaughtTimestamp = 0L;
    private CastBox lastCastBox;
    private boolean sameTarget;
    private int fishCaughtCounter = 1;
    private final int masterAnglerMinWaitLowerBound;
    private final int masterAnglerMaxWaitLowerBound;

    public FishingManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.FISHING);
        int bonusCapMin = McMMOMod.getAdvancedConfig().getFishingReductionMinWaitCap();
        int bonusCapMax = McMMOMod.getAdvancedConfig().getFishingReductionMaxWaitCap();

        this.masterAnglerMinWaitLowerBound = Math.max(bonusCapMin, 0);
        this.masterAnglerMaxWaitLowerBound = Math.max(bonusCapMax,
                masterAnglerMinWaitLowerBound + 40);
    }

    public boolean canShake() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.FISHING_SHAKE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FISHING_SHAKE);
    }

    public boolean canMasterAngler() {
        return getSkillLevel() >= RankUtils.getUnlockLevel(SubSkillType.FISHING_MASTER_ANGLER)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FISHING_MASTER_ANGLER);
    }

    /**
     * Whether the player has caught a fish within the last second. Mutates the internal
     * last-catch timestamp as a side effect (legacy parity). The "you're fishing too fast" warning
     * message is dropped — PORT once {@code NotificationManager}/{@code LocaleLoader.getText} land.
     *
     * @return whether the player has had a previous catch within the last second
     */
    public boolean isFishingTooOften() {
        long currentTime = System.currentTimeMillis();
        boolean hasFishedRecently = lastFishCaughtTimestamp + 1000 > currentTime;
        lastFishCaughtTimestamp = currentTime;
        return hasFishedRecently;
    }

    /**
     * Updates the fishing-exploit tracking state for a new cast, mirroring legacy
     * {@code processExploiting}. Retargeted from a Bukkit {@code Vector}/{@code BoundingBox} to raw
     * coordinates so the state machine is fully MC-free and unit-testable.
     *
     * @param castX the X coordinate of the center of the cast
     * @param castY the Y coordinate of the center of the cast
     * @param castZ the Z coordinate of the center of the cast
     */
    public void processExploiting(double castX, double castY, double castZ) {
        CastBox newCastBox = makeCastBox(castX, castY, castZ);
        this.sameTarget = lastCastBox != null && lastCastBox.overlaps(newCastBox);

        if (this.sameTarget) {
            fishCaughtCounter++;
        } else {
            fishCaughtCounter = 1;
        }

        if (!this.sameTarget) {
            lastCastBox = newCastBox;
        }
    }

    /**
     * @return true if the player is exploiting fishing (caught more fish than the configured limit
     *     without moving their fishing spot), false otherwise
     */
    public boolean isExploitingFishing() {
        return this.sameTarget && fishCaughtCounter >= McMMOMod.getExperienceConfig()
                .getFishingExploitingOptionOverFishLimit();
    }

    static CastBox makeCastBox(double x, double y, double z) {
        int exploitingRange = McMMOMod.getExperienceConfig().getFishingExploitingOptionMoveRange();
        double halfXZ = exploitingRange / 2.0;
        return new CastBox(x - halfXZ, x + halfXZ, y - 1, y + 1, z - halfXZ, z + halfXZ);
    }

    /**
     * MC-free replacement for the Bukkit {@code BoundingBox} legacy used only to detect whether two
     * casts overlap.
     */
    record CastBox(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        boolean overlaps(CastBox other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }

    /**
     * Gets the loot tier (the player's Treasure Hunter rank).
     *
     * @return the loot tier
     */
    public int getLootTier() {
        return RankUtils.getRank(getPlayer(), SubSkillType.FISHING_TREASURE_HUNTER);
    }

    public double getShakeChance() {
        return McMMOMod.getAdvancedConfig().getShakeChance(
                RankUtils.getRank(getPlayer(), SubSkillType.FISHING_SHAKE));
    }

    /**
     * The Shake sub-skill roll — whether reeling in this hooked mob shakes anything loose at all
     * (legacy {@code shakeCheck}'s leading {@code isStaticSkillRNGSuccessful}). Owns its RNG, like
     * {@link com.gmail.nossr50.skills.herbalism.HerbalismManager#rollGreenThumbReplant()}; the
     * <i>which drop</i> decision is the caller-fed {@link #rollShakeTreasure} below.
     *
     * @return whether the shake succeeded
     */
    public boolean rollShakeSuccess() {
        return ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.FISHING, mmoPlayer,
                getShakeChance());
    }

    /**
     * Pick which drop a successful Shake knocks off the target — legacy {@code Fishing.findPossibleDrops}
     * + {@code chooseDrop}, fused into one pure function. The random draw is supplied by the caller so
     * the whole selection is unit-testable, exactly as {@link #rollFishingTreasure} and
     * {@code HerbalismManager#rollHylianLuck} do.
     *
     * <p>Walks the entity's configured drops in config order accumulating their {@code Drop_Chance}s and
     * returns the first whose running total exceeds {@code dropRoll}. Legacy's quirks are preserved:
     * the roll is an <b>integer</b> in {@code [0, 100)} (so a {@code 0.5} chance still wins on a roll of
     * {@code 0}), and a list whose chances sum below 100 simply yields nothing on a high roll — that is
     * how the {@code PLAYER} section, whose every chance is {@code 0.0}, drops nothing at all.
     *
     * <p>Neither a treasure's {@code Drop_Level} nor its {@code XP} is consulted, because legacy's
     * {@code chooseDrop} consults neither (Shake pays the flat {@link #awardShakeXP()} instead) — see
     * {@link com.gmail.nossr50.datatypes.treasure.ShakeTreasure}.
     *
     * @param entityRegistryPath the target's vanilla entity registry path, e.g. {@code "cave_spider"}
     * @param dropRoll a fresh integer roll in {@code [0, 100)} (the caller's {@code nextInt(100)})
     * @return the drop to shake loose, or empty when this entity has no drops or the roll cleared them
     */
    public @NotNull Optional<ShakeTreasure> rollShakeTreasure(@NotNull String entityRegistryPath,
            int dropRoll) {
        final List<ShakeTreasure> possibleDrops = McMMOMod.getFishingTreasureConfig()
                .getShakeTreasures(entityRegistryPath);

        double cumulativeChance = 0.0;
        for (ShakeTreasure treasure : possibleDrops) {
            cumulativeChance += treasure.getDropChance();
            if (dropRoll < cumulativeChance) {
                return Optional.of(treasure);
            }
        }
        return Optional.empty();
    }

    /**
     * The damage a successful Shake deals to the target, from legacy {@code shakeCheck}: a quarter of
     * the mob's maximum health, floored at 1 and capped at 10 — "so you can shake a mob no more than 4
     * times", as legacy's own comment puts it (the cap is what saves high-health mobs from dying in
     * four shakes).
     *
     * @param maxHealth the target's maximum health
     * @return the damage to deal
     */
    public static double shakeDamage(double maxHealth) {
        return Math.min(Math.max(maxHealth / 4, 1), 10);
    }

    /**
     * Award the flat Shake XP ({@code Experience_Values.Fishing.Shake}). Legacy pays this on every
     * successful shake that actually drops something, independent of the drop's own configured XP.
     */
    public void awardShakeXP() {
        final int xp = McMMOMod.getExperienceConfig().getFishingShakeXP();
        if (xp <= 0) {
            return;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
    }

    /**
     * Award the base Fishing XP for a caught item (the XP slice of legacy {@code processFishing}, which
     * awarded {@code getXp(FISHING, fishingCatch.getType())}). The Treasure Hunter / Magic Hunter loot
     * and its bonus {@code treasureXp} are deferred until {@code FishingTreasureConfig} is ported —
     * that config is entirely additive on top of this base value, so wiring the base XP first stands
     * alone. The MC-typed caller ({@code fabric.listeners.FishingListener}) resolves the caught item's
     * material config string; this stays MC-free and unit-testable — same split as
     * {@link com.gmail.nossr50.skills.smelting.SmeltingManager#awardSmeltingXP(String)}.
     *
     * @param materialConfigString the caught item's material config string (see
     *     {@code ConfigStringUtils.getMaterialConfigString}), e.g. {@code "Cod"}
     */
    public void awardFishingXP(@NotNull String materialConfigString) {
        final int xp = McMMOMod.getExperienceConfig().getXp(PrimarySkillType.FISHING,
                materialConfigString);
        if (xp <= 0) {
            return; // caught item carries no configured Fishing XP (junk / treasure not in the table).
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
    }

    /**
     * Roll for a Treasure-Hunter fishing reward — the item core of legacy {@code getFishingTreasure}.
     * Pure (no RNG, no Minecraft): the caller supplies the two random draws, so the whole selection is
     * unit-testable, exactly as {@link #resolveMasterAnglerWaitTimes} did for Master Angler. Walks the
     * per-tier/per-rarity {@code Item_Drop_Rates} curve (most-rare first, cumulative) and returns the
     * chosen treasure, or empty when: fishing drops are disabled, the Treasure Hunter sub-skill is off,
     * the roll clears every rarity band (no treasure this catch), or the selected rarity bucket is empty.
     *
     * <p>The Magic Hunter enchant path (and its enchanted-book / {@code Shake} rewards) is still deferred
     * — see the class javadoc and {@link FishingTreasureConfig}. A rank-0 (unranked) player reads the
     * absent {@code Tier_0} rates as {@code 0.0}, so an ordinary positive roll misses naturally — no
     * rank gate is needed here (and there is no {@code rank-1} array index to overrun, unlike the Maces
     * Cripple landmine).
     *
     * @param diceRoll a fresh roll in {@code [0, 100)} (the caller's {@code nextDouble() * 100})
     * @param luckOfTheSea the Luck of the Sea level on the fishing rod (scales the roll toward rarer
     *     bands — legacy scales rather than subtracts so it never floors every drop chance)
     * @param bucketPicker picks an index in {@code [0, bucketSize)} for the chosen rarity (the caller's
     *     {@code nextInt(bound)}); only invoked for a non-empty bucket
     * @return the rolled treasure, or {@link Optional#empty()} if none was won
     */
    public @NotNull Optional<FishingTreasure> rollFishingTreasure(double diceRoll, int luckOfTheSea,
            @NotNull IntUnaryOperator bucketPicker) {
        if (!McMMOMod.getGeneralConfig().getFishingDropsEnabled()
                || !Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FISHING_TREASURE_HUNTER)) {
            return Optional.empty();
        }

        // Scale (not subtract) by luck so it never imposes a minimum drop chance on every catch.
        double scaledRoll = diceRoll * (1.0
                - luckOfTheSea * McMMOMod.getGeneralConfig().getFishingLureModifier() / 100.0);

        final int tier = getLootTier();
        final FishingTreasureConfig config = McMMOMod.getFishingTreasureConfig();

        for (Rarity rarity : Rarity.values()) {
            final double dropRate = config.getItemDropRate(tier, rarity);

            if (scaledRoll <= dropRate) {
                final List<FishingTreasure> rewards = config.fishingRewards.get(rarity);
                if (rewards.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(rewards.get(bucketPicker.applyAsInt(rewards.size())));
            }

            scaledRoll -= dropRate;
        }

        return Optional.empty();
    }

    /**
     * Award the Treasure-Hunter bonus XP for a rolled treasure. This is the {@code treasureXp} slice of
     * legacy {@code processFishing}, kept separate because this port already awards the base catch XP via
     * {@link #awardFishingXP(String)} (legacy summed both into one {@code applyXpGain}). A no-op when the
     * treasure carries no XP.
     *
     * @param treasureXp the rolled treasure's configured XP
     */
    public void awardFishingTreasureXP(int treasureXp) {
        if (treasureXp <= 0) {
            return;
        }
        applyXpGain(treasureXp, XPGainReason.PVE, XPGainSource.SELF);
    }

    protected int getVanillaXPBoostModifier() {
        return McMMOMod.getAdvancedConfig().getFishingVanillaXPModifier(getLootTier());
    }

    /**
     * Handle the vanilla XP boost for Fishing.
     *
     * @param experience The amount of experience initially awarded by the event
     * @return the modified event experience
     */
    public int handleVanillaXpBoost(int experience) {
        return experience * getVanillaXPBoostModifier();
    }

    public boolean isMagicHunterEnabled() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.FISHING_MAGIC_HUNTER)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.FISHING_TREASURE_HUNTER)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FISHING_MAGIC_HUNTER)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FISHING_TREASURE_HUNTER);
    }

    public int getReducedTicks(int ticks, int totalBonus, int tickBounds) {
        return Math.max(tickBounds, ticks - totalBonus);
    }

    public int getMasterAnglerTickMaxWaitReduction(int masterAnglerRank, boolean boatBonus,
            int emulatedLureBonus) {
        int totalBonus =
                McMMOMod.getAdvancedConfig().getFishingReductionMaxWaitTicks() * masterAnglerRank;

        if (boatBonus) {
            totalBonus += getFishingBoatMaxWaitReduction();
        }

        totalBonus += emulatedLureBonus;

        return totalBonus;
    }

    public int getMasterAnglerTickMinWaitReduction(int masterAnglerRank, boolean boatBonus) {
        int totalBonus =
                McMMOMod.getAdvancedConfig().getFishingReductionMinWaitTicks() * masterAnglerRank;

        if (boatBonus) {
            totalBonus += getFishingBoatMinWaitReduction();
        }

        return totalBonus;
    }

    public int getFishingBoatMinWaitReduction() {
        return McMMOMod.getAdvancedConfig().getFishingBoatReductionMinWaitTicks();
    }

    public int getFishingBoatMaxWaitReduction() {
        return McMMOMod.getAdvancedConfig().getFishingBoatReductionMaxWaitTicks();
    }

    /**
     * The Master Angler decision extracted from legacy {@code processMasterAngler}'s live
     * {@code FishHook} mutation: given the hook's current wait ticks and the player's situation, work
     * out the reduced wait ticks (and whether the caller must disable the hook's vanilla Lure
     * application to avoid the lure-level-above-3 Minecraft bug). Pure function — the
     * hook-mutating caller ({@code fabric.listeners.FishingListener#resolveWaitCountdown}) is a thin
     * wrapper around this.
     *
     * <p>Legacy took the Lure <i>enchantment level</i> and converted it to ticks itself; a caller that
     * already holds the reduction in ticks (as vanilla's bobber does) should use
     * {@link #resolveMasterAnglerWaitTimesFromLureTicks} instead and skip the enchant lookup.
     *
     * @param minWaitTicks the hook's current minimum wait ticks
     * @param maxWaitTicks the hook's current maximum wait ticks
     * @param masterAnglerRank the player's Master Angler rank
     * @param boatBonus whether the player is fishing from a boat
     * @param lureLevel the vanilla Lure enchantment level on the fishing rod
     * @return the resolved wait times
     */
    public MasterAnglerWaitTimes resolveMasterAnglerWaitTimes(int minWaitTicks, int maxWaitTicks,
            int masterAnglerRank, boolean boatBonus, int lureLevel) {
        return resolveMasterAnglerWaitTimesFromLureTicks(minWaitTicks, maxWaitTicks,
                masterAnglerRank, boatBonus, lureLevel * LURE_TICKS_PER_LEVEL);
    }

    /**
     * {@link #resolveMasterAnglerWaitTimes} taking the Lure reduction already expressed in ticks.
     *
     * <p>This is the faithful shape for the Fabric port: legacy computed
     * {@code convertedLureBonus = lureLevel * 100} from the Bukkit enchantment level, and vanilla's
     * {@code FishingBobberEntity.waitTimeReductionTicks} — the value the bobber subtracts from its
     * freshly drawn wait countdown — is <i>exactly</i> that same figure (the {@code Lure} enchantment's
     * {@code fishing_time_reduction} effect is 5 seconds per level, i.e. {@value #LURE_TICKS_PER_LEVEL}
     * ticks per level). So the mixin can hand us the bobber's own field and no enchantment-registry
     * read is needed — and a non-vanilla Lure of any level converts without the integer-level rounding
     * the {@code lureLevel} overload would impose.
     *
     * @param minWaitTicks the hook's current minimum wait ticks
     * @param maxWaitTicks the hook's current maximum wait ticks
     * @param masterAnglerRank the player's Master Angler rank
     * @param boatBonus whether the player is fishing from a boat
     * @param lureReductionTicks the wait-tick reduction the hook's Lure enchantment would apply
     * @return the resolved wait times
     */
    public MasterAnglerWaitTimes resolveMasterAnglerWaitTimesFromLureTicks(int minWaitTicks,
            int maxWaitTicks, int masterAnglerRank, boolean boatBonus, int lureReductionTicks) {
        // This avoids a Minecraft bug where lure levels above 3 break fishing
        boolean disableLure = lureReductionTicks > 0;
        int convertedLureBonus = disableLure ? lureReductionTicks : 0;

        int minWaitReduction = getMasterAnglerTickMinWaitReduction(masterAnglerRank, boatBonus);
        int maxWaitReduction = getMasterAnglerTickMaxWaitReduction(masterAnglerRank, boatBonus,
                convertedLureBonus);

        int reducedMinWaitTime = getReducedTicks(minWaitTicks, minWaitReduction,
                masterAnglerMinWaitLowerBound);
        int reducedMaxWaitTime = getReducedTicks(maxWaitTicks, maxWaitReduction,
                masterAnglerMaxWaitLowerBound);

        if (reducedMaxWaitTime < reducedMinWaitTime) {
            reducedMaxWaitTime = reducedMinWaitTime + 100;
        }

        return new MasterAnglerWaitTimes(reducedMinWaitTime, reducedMaxWaitTime, disableLure);
    }

    /**
     * @param minWaitTicks the resolved minimum wait ticks to apply to the hook
     * @param maxWaitTicks the resolved maximum wait ticks to apply to the hook
     * @param disableLure whether the caller must disable the hook's vanilla Lure application
     */
    public record MasterAnglerWaitTimes(int minWaitTicks, int maxWaitTicks, boolean disableLure) {
    }

    public int getMasterAnglerMinWaitLowerBound() {
        return masterAnglerMinWaitLowerBound;
    }

    public int getMasterAnglerMaxWaitLowerBound() {
        return masterAnglerMaxWaitLowerBound;
    }
}
