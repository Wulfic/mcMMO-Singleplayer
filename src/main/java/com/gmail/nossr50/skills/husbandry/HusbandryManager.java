package com.gmail.nossr50.skills.husbandry;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.function.ToIntFunction;

/**
 * Husbandry — the livestock lifecycle skill. XP comes from six verbs spanning an animal's whole life
 * under your care: breed it, raise it to adulthood, feed a baby along, shear it, harvest its hive,
 * and milk or brush it.
 *
 * <p><b>MC-free by construction</b>, like every other manager. The platform layer decides
 * <em>which</em> verb happened and to <em>what</em> animal; this class owns the pricing, so all of it
 * is unit-testable.
 *
 * <h2>The boundary against Taming — read this before adding anything</h2>
 * <b>The line is the verb, never the species.</b> A species split is not even available: the shipped
 * {@code Experience_Values.Taming.Animal_Taming} table already claims every animal in the game, down
 * to bees and goats you cannot actually tame. So:
 *
 * <blockquote><b>Taming pays once, for making an animal yours. Husbandry pays repeatedly, for what
 * you do with it afterwards.</b></blockquote>
 *
 * Breeding a tamed wolf pays Husbandry at the full rate — the verb owns it, not the species.
 * Feeding a wolf to heal it stays Taming ({@code TAMING_FAST_FOOD_SERVICE}); healing <em>in
 * combat</em> is the discriminator. Any sub-skill that fails that test does not ship.
 *
 * <h2>Why breeding is a per-species table and the rest are flat</h2>
 * Breeding costs whatever the animal's breeding item costs, and that spans two orders of magnitude —
 * chicken seeds are free, a horse eats golden carrots, a sniffer needs a torchflower seed dug out of
 * suspicious sand. Paying one flat rate would make the cheapest animal in the game the only one
 * worth breeding. The harvest verbs have no such spread: a shear is a shear.
 *
 * <p><b>An unpriced species pays nothing</b>, deliberately — the table <em>is</em> the definition of
 * what this skill rewards, exactly as {@code Animal_Taming} is for Taming, so a mob added by a future
 * version or another mod cannot silently start paying a number nobody chose.
 *
 * <h2>Stage 0</h2>
 * This class prices the six verbs and nothing else. No mechanic calls it yet; the trigger layer and
 * the sub-skills land in stages 1–6 (see {@code plans/new-skills/husbandry.md}). The pricing ships
 * first, alone, because it is the part with no Minecraft in it at all.
 */
public class HusbandryManager extends SkillManager {

    /**
     * XP for feeding a baby animal to speed its growth along.
     *
     * <p>Small on purpose. It is the one verb a player can repeat as fast as they can click, limited
     * only by how much food they are holding, so it is priced as a nudge toward the raise payout
     * rather than as an income of its own.
     */
    public static final int DEFAULT_FEED_BABY_XP = 50;

    /** XP for shearing a sheep, mooshroom, snow golem or bogged. */
    public static final int DEFAULT_SHEAR_XP = 300;

    /** XP for harvesting honey or honeycomb from a hive or nest. */
    public static final int DEFAULT_HIVE_XP = 500;

    /** XP for milking a cow, or bucketing a mooshroom's stew. */
    public static final int DEFAULT_MILK_XP = 200;

    /** XP for brushing an armadillo's scute off. */
    public static final int DEFAULT_BRUSH_XP = 300;

    /**
     * What a raised animal pays, as a multiple of what breeding it paid.
     *
     * <p>Shipped at {@code 1.0} — raising pays the same as breeding. The two halves are deliberately
     * equal because the raise half is the one part of this skill that cannot be rushed: it is twenty
     * real minutes of vanilla time per animal, and the only way to shorten it is to spend food on
     * the feed verb. Making the unrushable half worth as much as the clickable half is what stops
     * the skill collapsing into "spam the breeding item".
     */
    public static final double DEFAULT_RAISE_MULTIPLIER = 1.0;

    /**
     * The furthest Multi-Breed can ever reach, in blocks, whatever {@code advanced.yml} says.
     *
     * <p>The wiki's own number, and it is a hard clamp rather than just a default because the radius
     * is the input to a per-activation entity sweep: a mistyped 400 would scan a box eight chunks
     * across on every animal a player feeds.
     */
    public static final double HARD_MAX_MULTI_BREED_RADIUS = 40.0;

    /** Multi-Breed's reach at the moment it unlocks, before any level scaling. */
    public static final double DEFAULT_MULTI_BREED_BASE_RADIUS = 4.0;

    /** Multi-Breed's reach at {@code MaxBonusLevel}. */
    public static final double DEFAULT_MULTI_BREED_MAX_RADIUS = HARD_MAX_MULTI_BREED_RADIUS;

    /**
     * How many <em>extra</em> animals one breeding item can set in love at {@code MaxBonusLevel}.
     *
     * <p><b>This number, not the radius, is the anti-exploit gate — read this before raising it.</b>
     * Husbandry pays per breeding, and Multi-Breed is the only thing in the skill that turns one
     * player action into many breedings. Left uncapped at the 40-block radius, one wheat thrown into
     * a hundred-cow pen would pay fifty breedings at once, repeatable as fast as a player can click,
     * and the whole XP budget in {@code plans/new-skills/husbandry.md} (~51 h of active breeding to
     * max) would collapse to under an hour.
     *
     * <p>Capping the <em>count</em> rather than the radius keeps what makes the sub-skill good — you
     * reach across the pen instead of walking to each animal — while keeping the XP per click
     * bounded. It is the same shape as Unarmored's per-attacker award cap and Agility's Dodge cap:
     * the port's answer to a repeatable award has consistently been a hard ceiling on awards, not a
     * softer rate.
     *
     * <p><b>Ruled at four, down from the eight stage 1 shipped</b> (2026-07-29). Four keeps
     * Multi-Breed a convenience — you feed the pen from where you stand instead of chasing each
     * animal — rather than making it the skill's primary income: at eight, one click was worth nine
     * breedings and the sub-skill quietly became the only sensible way to level Husbandry at all.
     */
    public static final int DEFAULT_MULTI_BREED_MAX_ADDITIONAL_ANIMALS = 4;

    /**
     * How much of a newborn's childhood {@code Accelerated Growth} removes at {@code MaxBonusLevel},
     * as a fraction.
     *
     * <p>Deliberately modest. The raise verb is the one part of this skill that cannot be rushed —
     * twenty real minutes of vanilla time per animal — and that unrushability is the whole reason it
     * pays as much as breeding does. An acceleration large enough to collapse the wait would turn
     * the skill's slowest, safest income into its fastest.
     */
    public static final double DEFAULT_MAX_GROWTH_ACCELERATION = 0.30;

    /**
     * The most of a newborn's childhood that may ever be skipped, whatever {@code advanced.yml} says.
     *
     * <p><b>A hard clamp rather than a default, because the degenerate value is an exploit and not
     * merely a silly one.</b> At an acceleration of 1.0 a newborn's breeding age would be shortened
     * all the way to zero, which is not "grows up instantly" but "crosses the baby→adult boundary
     * inside the breeding call" — the raise verb would pay out in the same tick as the breed verb,
     * for every animal, forever. {@link #applyGrowthAcceleration} additionally floors the result at
     * one tick of childhood so that the transition cannot happen there even if this clamp is
     * someday raised.
     */
    public static final double HARD_MAX_GROWTH_ACCELERATION = 0.90;

    /**
     * Bountiful Harvest's chance at max level to spare the tool a harvest would have worn, in
     * percent.
     *
     * <p>Lower than the bonus-drop chance on purpose. A bonus drop is a windfall a player notices
     * and enjoys; a durability save is felt only as "my shears last longer", so a large number here
     * buys much less than the same number spent on drops — and at 100 it would quietly make shears
     * an infinite tool, which is a different game.
     */
    public static final double DEFAULT_HARVEST_DURABILITY_SAVE_CHANCE = 25.0;

    public HusbandryManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.HUSBANDRY);
    }

    /** {@code null} in a unit test with no config bound, and during very early boot. */
    private static ExperienceConfig experience() {
        return McMMOMod.getExperienceConfig();
    }

    /** Clamps a configured XP value to zero: a mistyped config must never pay negative XP. */
    private static float atLeastZero(double xp) {
        return xp <= 0 ? 0F : (float) xp;
    }

    // --- Breed ------------------------------------------------------------------------------

    /**
     * XP for breeding a pair of the given species.
     *
     * <p>Paid <b>once per breeding, not once per parent</b> — the caller sits on vanilla's
     * {@code AnimalEntity#breed}, which runs once for the pair.
     *
     * @param entityConfigString the animal's config key, e.g. {@code "Cow"} (see
     *                           {@code ConfigStringUtils#getConfigEntityTypeString})
     * @return the XP to award; {@code 0} for a species the shipped table does not price
     */
    public float getBreedXp(String entityConfigString) {
        if (entityConfigString == null) {
            return 0F;
        }
        final ExperienceConfig experience = experience();
        if (experience == null) {
            return 0F;
        }
        return atLeastZero(experience.getHusbandryBreedXp(entityConfigString));
    }

    /**
     * Credit one successful breeding.
     *
     * <p>Called once per <b>breeding</b>, never once per parent. The trigger layer sits on the single
     * point vanilla itself uses to record "this player bred these two animals", so the pair arrives
     * here already collapsed into one event.
     *
     * @param entityConfigString the animal's config key, e.g. {@code "Cow"}
     * @return the XP awarded, or {@code 0} for a species the table does not price
     */
    public float onBreed(String entityConfigString) {
        final float xp = getBreedXp(entityConfigString);
        if (xp <= 0) {
            return 0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }

    // --- Sub-skill: Twins ---------------------------------------------------------------------

    public boolean canTwins() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_TWINS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HUSBANDRY_TWINS);
    }

    /**
     * Whether this breeding should produce a second baby.
     *
     * <p>Chance scales with level up to {@code Skills.Husbandry.Twins.ChanceMax}, which ships at
     * <b>25 %</b> rather than the wiki's 100 %. Doubling every breed at max level is a food and
     * mob-population firehose on its own, and it multiplies with Multi-Breed rather than adding to
     * it — the two together at 100 % would turn one item into a whole herd. A quarter keeps a twin
     * birth a pleasant surprise at max rank instead of the expected outcome.
     *
     * @return {@code true} if a twin should be born
     */
    public boolean rollTwins() {
        return canTwins()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HUSBANDRY_TWINS, mmoPlayer);
    }

    // --- Sub-skill: Multi-Breed ---------------------------------------------------------------

    public boolean canMultiBreed() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_MULTI_BREED)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HUSBANDRY_MULTI_BREED);
    }

    /**
     * How far one breeding item reaches, in blocks.
     *
     * <p>Grows from {@link #DEFAULT_MULTI_BREED_BASE_RADIUS} at unlock to the configured maximum at
     * {@code MaxBonusLevel}, and is clamped to {@link #HARD_MAX_MULTI_BREED_RADIUS} whatever the
     * config says — this value sizes an entity sweep that runs every time a player feeds an animal.
     *
     * @return the search radius, or {@code 0} when Multi-Breed is locked
     */
    public double getMultiBreedRadius() {
        if (!canMultiBreed()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return DEFAULT_MULTI_BREED_BASE_RADIUS;
        }
        final double base = Math.max(0.0, advanced.getMultiBreedBaseRadius());
        final double max = Math.max(base, advanced.getMultiBreedMaxRadius());
        final double scaled = base + scaleToLevel(max - base, advanced.getMultiBreedMaxBonusLevel());
        return Math.min(HARD_MAX_MULTI_BREED_RADIUS, scaled);
    }

    /**
     * How many <em>additional</em> animals one breeding item may set in love, beyond the one the
     * player actually fed.
     *
     * <p>See {@link #DEFAULT_MULTI_BREED_MAX_ADDITIONAL_ANIMALS} for why this cap exists and why it
     * is the count rather than the radius that carries it. Scales from one at unlock to the
     * configured maximum at {@code MaxBonusLevel}.
     *
     * @return the cap, or {@code 0} when Multi-Breed is locked or configured off
     */
    public int getMultiBreedMaxAdditionalAnimals() {
        if (!canMultiBreed()) {
            return 0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final int max = advanced == null
                ? DEFAULT_MULTI_BREED_MAX_ADDITIONAL_ANIMALS
                : advanced.getMultiBreedMaxAdditionalAnimals();
        if (max <= 0) {
            return 0; // Configured off: the sub-skill still "unlocks" but spreads to nobody.
        }
        final int maxBonusLevel = advanced == null
                ? 0
                : advanced.getMultiBreedMaxBonusLevel();
        // Floor of one: an unlocked Multi-Breed that reaches nobody at all would read as broken.
        return Math.max(1, (int) Math.floor(1 + scaleToLevel(max - 1, maxBonusLevel)));
    }

    // --- Raise ------------------------------------------------------------------------------

    /**
     * XP for an animal <em>you bred</em> reaching adulthood, roughly twenty minutes later.
     *
     * <p>Derived from the same per-species table as {@link #getBreedXp} rather than getting a second
     * table of its own, so a species can never be priced in one half of its lifecycle and not the
     * other. It follows that an unpriced species pays nothing here either.
     *
     * @param entityConfigString the animal's config key, e.g. {@code "Cow"}
     */
    public float getRaiseXp(String entityConfigString) {
        final float breedXp = getBreedXp(entityConfigString);
        if (breedXp <= 0) {
            return 0F;
        }
        return atLeastZero(breedXp * getRaiseMultiplier());
    }

    /** The configured raise-to-breed ratio; never negative. */
    public double getRaiseMultiplier() {
        final ExperienceConfig experience = experience();
        if (experience == null) {
            return DEFAULT_RAISE_MULTIPLIER;
        }
        return Math.max(0.0, experience.getHusbandryRaiseMultiplier());
    }

    /**
     * Credit one animal <em>this player bred</em> reaching adulthood.
     *
     * <p>Paid <b>once per animal</b>. The trigger layer holds that guarantee, not this method: it
     * fires only on the actual baby→adult breeding-age transition and drops the bred-by marker as it
     * pays, so a second crossing has nobody to credit.
     *
     * @param entityConfigString the animal's config key, e.g. {@code "Cow"}
     * @return the XP awarded, or {@code 0} for a species the table does not price
     */
    public float onRaise(String entityConfigString) {
        final float xp = getRaiseXp(entityConfigString);
        if (xp <= 0) {
            return 0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }

    /**
     * Credit one baby fed to hurry it along.
     *
     * <p><b>Gated on the species being priced for breeding</b>, even though the payout itself is
     * flat. Stage 0 settled that the breeding table <em>is</em> the definition of what this skill
     * rewards, and the feed verb has to obey the same rule or it becomes the hole in it — vanilla
     * lets you feed a few animals nothing else in the skill will ever pay for (a dolphin takes fish
     * through this exact path), and a modded mob would start paying a flat rate nobody chose.
     *
     * @param entityConfigString the animal's config key, e.g. {@code "Cow"}
     * @return the XP awarded, or {@code 0} for a species the breeding table does not price
     */
    public float onFeedBaby(String entityConfigString) {
        if (getBreedXp(entityConfigString) <= 0) {
            return 0F;
        }
        final float xp = getFeedBabyXp();
        if (xp <= 0) {
            return 0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }

    // --- Sub-skill: Accelerated Growth ---------------------------------------------------------

    public boolean canAcceleratedGrowth() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_ACCELERATED_GROWTH)
                && Permissions.isSubSkillEnabled(getPlayer(),
                        SubSkillType.HUSBANDRY_ACCELERATED_GROWTH);
    }

    /**
     * What fraction of a newborn's childhood this player's stock skips, as {@code 0.0}–
     * {@link #HARD_MAX_GROWTH_ACCELERATION}.
     *
     * @return the fraction, or {@code 0} when Accelerated Growth is locked
     */
    public double getGrowthAcceleration() {
        if (!canAcceleratedGrowth()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final double max = advanced == null
                ? DEFAULT_MAX_GROWTH_ACCELERATION
                : advanced.getMaxGrowthAcceleration();
        if (max <= 0) {
            return 0.0;
        }
        final int maxBonusLevel = advanced == null
                ? 0
                : advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH);
        return Math.min(HARD_MAX_GROWTH_ACCELERATION, scaleToLevel(max, maxBonusLevel));
    }

    /**
     * Shorten a newborn's childhood by this player's Accelerated Growth.
     *
     * <p>Applied once, at birth, rather than by speeding the animal's ageing up every tick. The
     * outcome a player sees is identical — the baby is an adult sooner — and it keeps the whole
     * sub-skill off the tick path, where a per-baby lookup would run for every baby animal in every
     * loaded chunk for twenty minutes at a time.
     *
     * <p><b>The result is always still a baby.</b> Breeding ages run negative and count up toward
     * zero, so a large enough acceleration would land exactly on zero — which reads to the raise
     * hook as the baby→adult transition and would pay the raise verb in the same tick as the breed
     * verb. Flooring at {@code -1} makes that structurally impossible rather than merely unlikely.
     *
     * @param breedingAge the newborn's age as vanilla set it — negative, e.g. {@code -24000}
     * @return the shortened age, never zero or positive, and never older than it started
     */
    public int applyGrowthAcceleration(int breedingAge) {
        if (breedingAge >= 0) {
            return breedingAge; // Not a baby; nothing to shorten.
        }
        final double acceleration = getGrowthAcceleration();
        if (acceleration <= 0) {
            return breedingAge;
        }
        final int shortened = (int) Math.round(breedingAge * (1.0 - acceleration));
        return Math.min(-1, shortened);
    }

    /**
     * Whether this feed should count twice.
     *
     * <p>Accelerated Growth's active half: the passive half shortens the childhood of animals you
     * bred, this one rewards actually standing there feeding them. Chance scales with level up to
     * {@code Skills.Husbandry.AcceleratedGrowth.ChanceMax}.
     */
    public boolean rollDoubleFeed() {
        return canAcceleratedGrowth()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH,
                        mmoPlayer);
    }

    /**
     * How much growth one feed actually grants, after Accelerated Growth's double-feed roll.
     *
     * @param growthSeconds the seconds of growth vanilla was about to grant — always positive at the
     *                      feed sites (vanilla negates the remaining childhood before converting it)
     * @return {@code growthSeconds}, or twice that on a successful roll
     */
    public int applyFeedBonus(int growthSeconds) {
        if (growthSeconds <= 0) {
            return growthSeconds;
        }
        return rollDoubleFeed() ? growthSeconds * 2 : growthSeconds;
    }

    // --- Shear ------------------------------------------------------------------------------

    /**
     * Credit one animal sheared by hand.
     *
     * <p><b>Not gated on the breeding table</b>, unlike the feed verb — and the difference is not an
     * oversight. Feeding routes through a method vanilla shares with animals this skill has nothing
     * to do with, so it needs the table to say which of them count. Shearing has no such spread:
     * the trigger layer sits on vanilla's shear-loot funnel, which only four entities in the game
     * reach, and all four are livestock this skill means to pay for. A flat rate is also the honest
     * pricing — the plan's own note that "a shear is a shear" — since shears cost the same whichever
     * animal you point them at.
     *
     * @return the XP awarded, or {@code 0} if the verb is priced at nothing
     */
    public float onShear() {
        final float xp = getShearXp();
        if (xp <= 0) {
            return 0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }

    // --- Sub-skill: Bountiful Harvest -----------------------------------------------------------

    public boolean canBountifulHarvest() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST)
                && Permissions.isSubSkillEnabled(getPlayer(),
                        SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST);
    }

    /**
     * Whether this harvest should yield a second helping of what it just dropped.
     *
     * <p>The harvest family's headline effect, and the one shared reward path behind shearing now
     * and hive, milk and brush in stage 4 — written once here rather than four times at the call
     * sites. Chance scales with level up to {@code Skills.Husbandry.BountifulHarvest.ChanceMax}.
     *
     * <p>Doubling the <em>drop</em> rather than granting a fixed item is what keeps this honest
     * across species: a sheep's roll already depends on its colour, a mooshroom's on its variant,
     * and the bonus inherits all of that for free instead of re-deriving a table that would rot.
     */
    public boolean rollBonusHarvestDrop() {
        return canBountifulHarvest()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST,
                        mmoPlayer);
    }

    /**
     * Whether this harvest should cost the tool no durability at all.
     *
     * <p>Bountiful Harvest's second, quieter effect. Scaled by hand from
     * {@link #getHarvestDurabilitySaveChance} rather than through the sub-skill's own probability
     * because one sub-skill drives two independent rolls and {@code ProbabilityUtil} keys its
     * chance off the {@code SubSkillType} — the same split Accelerated Growth already makes between
     * its childhood-shortening half and its double-feed half.
     */
    public boolean rollToolDurabilitySave() {
        final double chance = getHarvestDurabilitySaveChance();
        return chance > 0
                && ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.HUSBANDRY, mmoPlayer,
                        chance);
    }

    /**
     * This player's current chance to save a harvesting tool's durability, in percent.
     *
     * <p>Public because {@code /mcstats} renders it as the sub-skill's second stat line; the roll
     * itself is {@link #rollToolDurabilitySave}.
     *
     * @return {@code 0}–100, or {@code 0} when Bountiful Harvest is locked
     */
    public double getHarvestDurabilitySaveChance() {
        if (!canBountifulHarvest()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final double max = advanced == null
                ? DEFAULT_HARVEST_DURABILITY_SAVE_CHANCE
                : advanced.getBountifulHarvestDurabilitySaveChance();
        if (max <= 0) {
            return 0.0;
        }
        final int maxBonusLevel = advanced == null
                ? 0
                : advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST);
        return Math.min(100.0, scaleToLevel(max, maxBonusLevel));
    }

    // --- The flat verbs ---------------------------------------------------------------------

    /** XP for feeding a baby animal to accelerate its growth. */
    public float getFeedBabyXp() {
        return flatXp(DEFAULT_FEED_BABY_XP, ExperienceConfig::getHusbandryFeedBabyXp);
    }

    /** XP for shearing a sheep, mooshroom, snow golem or bogged. */
    public float getShearXp() {
        return flatXp(DEFAULT_SHEAR_XP, ExperienceConfig::getHusbandryShearXp);
    }

    /** XP for harvesting a hive or bee nest. */
    public float getHiveXp() {
        return flatXp(DEFAULT_HIVE_XP, ExperienceConfig::getHusbandryHiveXp);
    }

    /** XP for milking a cow or bucketing mooshroom stew. */
    public float getMilkXp() {
        return flatXp(DEFAULT_MILK_XP, ExperienceConfig::getHusbandryMilkXp);
    }

    /** XP for brushing an armadillo. */
    public float getBrushXp() {
        return flatXp(DEFAULT_BRUSH_XP, ExperienceConfig::getHusbandryBrushXp);
    }

    /**
     * The shared read for the five flat verbs: shipped default when no config is bound, otherwise
     * the configured value clamped at zero.
     *
     * <p>Written once rather than five times because the fallback is the interesting half — five
     * copies is five chances to hand back a raw config value that a typo has made negative.
     */
    private static float flatXp(int shippedDefault, ToIntFunction<ExperienceConfig> read) {
        final ExperienceConfig experience = experience();
        if (experience == null) {
            return shippedDefault;
        }
        return atLeastZero(read.applyAsInt(experience));
    }
}
