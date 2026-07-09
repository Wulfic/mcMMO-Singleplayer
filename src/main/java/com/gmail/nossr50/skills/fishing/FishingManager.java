package com.gmail.nossr50.skills.fishing;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;
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
 * <p><b>Deferred until the entity/item/block adapters and {@code FishingTreasureConfig} port
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code processFishing}/{@code getFishingTreasure}/{@code processMagicHunter}/
 *       {@code getPossibleEnchantments} — Treasure Hunter + Magic Hunter item rolls, need
 *       {@code FishingTreasureConfig}'s rarity tables (still unported, same gap noted for
 *       Excavation's Hylian Luck) plus {@code ItemStack}/{@code Enchantment} construction;</li>
 *   <li>{@code shakeCheck} — needs the {@code LivingEntity} target, {@code Fishing}'s shake-drop
 *       tables, and item-spawn/damage adapters;</li>
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
     * application to avoid the lure-level-above-3 Minecraft bug). Pure function — the eventual
     * {@code FishHook}-mutating caller becomes a thin wrapper around this.
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
        // This avoids a Minecraft bug where lure levels above 3 break fishing
        boolean disableLure = lureLevel > 0;
        int convertedLureBonus = disableLure ? lureLevel * 100 : 0;

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
