package com.gmail.nossr50.skills.husbandry;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.treasure.HusbandryTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.DoublePredicate;
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
     * How many breedings may pay Husbandry XP inside one
     * {@link #DEFAULT_BREED_XP_AWARD_WINDOW_SECONDS window}.
     *
     * <p><b>This is the skill's anti-exploit gate — read this before raising it.</b> Husbandry pays
     * per breeding, and Multi-Breed turns one player action into many breedings, so something has to
     * bound how much XP a pen can produce.
     *
     * <h2>Why the cap moved off the breeding and onto the payout (GitHub #3, 2026-08-04)</h2>
     * Until now the gate was {@code MultiBreed.MaxAdditionalAnimals}, a ceiling of four on how many
     * animals one breeding item could set in love. It had two problems and the second one is fatal:
     *
     * <ul>
     *   <li>It taxed the <em>mechanic</em> rather than the reward. The sub-skill's whole appeal is
     *       feeding the pen from where you stand, and a cap of four meant walking to the rest of the
     *       herd anyway — which is what the issue reports as painful.</li>
     *   <li><b>It never actually bounded the XP rate.</b> It bounded XP <em>per item</em>, and wheat
     *       is free. Twenty clicks in one breath set a hundred animals in love and paid every one of
     *       the resulting fifty breedings, so the exploit the cap was written against — a big pen and
     *       a wheat farm — went straight through it.</li>
     * </ul>
     *
     * A cap counted <em>per unit of time</em> closes that, because time is the one input a farm
     * cannot manufacture. It is also the same shape the port has reached for twice already:
     * Unarmored's per-attacker award cap and Agility's Dodge cap both bound the award rather than the
     * mechanic.
     *
     * <p><b>Eight, doubled from the old four</b>, per the issue. At the shipped cow price of 350 that
     * is 336,000 XP/h sustained — comfortably above the skill's ~51 h design budget, so the cap does
     * not bite a player breeding by hand or working a normal pen. It bites only the loop that repeats
     * forever.
     */
    public static final int DEFAULT_BREED_XP_AWARDS_PER_WINDOW = 8;

    /**
     * How long one breed-XP award window lasts, in seconds.
     *
     * <p><b>Derived, not tuned: it is vanilla's own love duration.</b>
     * {@code AnimalEntity#lovePlayer} sets {@code loveTicks = 600} (bytecode-verified), so an animal
     * a player feeds forgets about it after exactly thirty seconds. Every breeding one breeding item
     * can possibly cause therefore lands inside a single window, which is what makes this cap
     * readable as <em>"one handful of feed pays at most {@value #DEFAULT_BREED_XP_AWARDS_PER_WINDOW}
     * breedings"</em> — precisely the job the old per-item cap was doing, now expressed in a unit a
     * wheat farm cannot inflate.
     *
     * <p>Do not shorten this to "make it fairer". A window briefer than vanilla's love duration
     * splits one click's burst across two windows and quietly doubles the effective cap.
     */
    public static final int DEFAULT_BREED_XP_AWARD_WINDOW_SECONDS = 30;

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

    /**
     * Beekeeper's chance at max level to yield an extra helping from a hive, in percent.
     *
     * <p>Modest, because it stacks on top of {@code Bountiful Harvest} rather than replacing it and
     * because a full hive already hands over three honeycombs at once.
     */
    public static final double DEFAULT_BONUS_HONEY_CHANCE = 30.0;

    /**
     * How long one animal must wait before it can pay a harvest award again, in seconds.
     *
     * <p>Five minutes, matching the length of vanilla's own post-breeding love cooldown so the two
     * clocks a player has to hold in their head are the same clock. See
     * {@link #getHarvestCooldownSeconds()} for which verbs it covers and why the other two are exempt.
     */
    public static final int DEFAULT_HARVEST_COOLDOWN_SECONDS = 300;

    /**
     * How far toward the best possible value {@code Selective Breeding} nudges an offspring stat at
     * {@code MaxBonusLevel}, as a fraction of the distance remaining.
     *
     * <p>Modest on purpose, because the effect compounds down the generations: every foal is bred from
     * parents that were themselves biased, so a bias that felt fair for one breeding walks a line
     * toward the ceiling much faster than it looks. A quarter of the remaining gap per generation
     * still reaches near-perfect stock in a handful of generations, which is the fantasy — it just
     * takes the handful.
     */
    public static final double DEFAULT_MAX_STAT_BIAS = 0.25;

    /**
     * The most of the remaining gap a single roll may ever be nudged, whatever {@code advanced.yml}
     * says.
     *
     * <p>A hard clamp rather than a default because {@code 1.0} would not be "very good horses", it
     * would be <b>every</b> horse at exactly the species maximum, permanently, from the first
     * breeding — which deletes horse breeding as an activity rather than rewarding it.
     */
    public static final double HARD_MAX_STAT_BIAS = 0.50;

    /**
     * {@code Brood}'s chance at max level that a hatching egg yields a full clutch instead of one
     * chick, in percent.
     *
     * <p>Vanilla's own rare case is 1-in-32 <em>of a hatch</em>. This is the same escalation
     * {@code Twins} got ruled down to: pleasant surprise at max rank, not the expected outcome.
     */
    public static final double DEFAULT_MULTI_CHICK_CHANCE = 20.0;

    /**
     * Herdsman's Call's minimum duration in ticks — ten seconds, a floor under the standard
     * super-ability length machinery rather than the length itself.
     */
    public static final int DEFAULT_HERDSMANS_CALL_DURATION_TICKS = 200;

    /** How far Herdsman's Call reaches at its shipped default, in blocks. */
    public static final double DEFAULT_HERD_RADIUS = 16.0;

    /**
     * The furthest Herdsman's Call can ever reach, whatever {@code advanced.yml} says.
     *
     * <p>The same ceiling Multi-Breed has, and a harder requirement here: this radius sizes an entity
     * sweep that runs <b>every tick</b> for the ability's whole duration, where Multi-Breed's runs once
     * per activation.
     */
    public static final double HARD_MAX_HERD_RADIUS = 40.0;

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
     * What one breeding was worth, and whether it was the moment the award cap started biting.
     *
     * @param xp         the XP awarded; {@code 0} when the species is unpriced <em>or</em> when this
     *                   window's awards are already spent
     * @param capReached {@code true} only on the <b>first</b> breeding a window refuses. The caller
     *                   uses it to tell the player once per window rather than once per breeding —
     *                   a gate that silently pays nothing is indistinguishable from a broken one,
     *                   and a gate that says so on every breeding in a hundred-cow pen is worse
     */
    public record BreedAward(float xp, boolean capReached) {

        /** Whether this breeding actually moved the player's XP. */
        public boolean paid() {
            return xp > 0;
        }
    }

    /** A breeding that reached neither the price table nor the cap. */
    private static final BreedAward NOTHING = new BreedAward(0F, false);

    /**
     * Whether the award window is open at all — distinct from a start tick of zero, which is a
     * perfectly ordinary world time on a freshly created world.
     */
    private boolean breedWindowOpen;

    /** The world tick the current award window opened on; meaningless while {@link #breedWindowOpen} is false. */
    private long breedWindowStartTick;

    /** Awards already spent in the current window. */
    private int breedAwardsThisWindow;

    /** Whether the player has already been told the cap bit, in the current window. */
    private boolean breedCapAnnouncedThisWindow;

    /**
     * Credit one successful breeding, unless this window's award cap is already spent.
     *
     * <p>Called once per <b>breeding</b>, never once per parent. The trigger layer sits on the single
     * point vanilla itself uses to record "this player bred these two animals", so the pair arrives
     * here already collapsed into one event.
     *
     * <p><b>The cap is claimed after the price, never before.</b> A species the table does not price
     * must not burn a slot on its way to paying nothing, or a pen of dolphins would throttle the cows
     * next to it.
     *
     * @param entityConfigString the animal's config key, e.g. {@code "Cow"}
     * @param worldTick          the world's current tick — the clock this cap is measured on. Passed
     *                           in rather than read, both to keep this class free of Minecraft and
     *                           because it makes the whole window assertable without a fake clock
     * @return what the breeding paid, and whether this was the moment the cap started biting
     */
    public BreedAward onBreed(String entityConfigString, long worldTick) {
        final float xp = getBreedXp(entityConfigString);
        if (xp <= 0) {
            return NOTHING;
        }
        if (!claimBreedAward(worldTick)) {
            final boolean announce = !breedCapAnnouncedThisWindow;
            breedCapAnnouncedThisWindow = true;
            return new BreedAward(0F, announce);
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return new BreedAward(xp, false);
    }

    /**
     * Spend one of this window's breed-XP awards.
     *
     * <p>A fixed window rather than a sliding one: the window opens on the first award after the last
     * one expired and runs for a fixed length, so at a boundary a player can collect up to two
     * windows' worth in quick succession. That is deliberate and harmless — the burst is bounded at
     * twice the cap and the sustained rate is exactly the cap — and it costs one long and one int of
     * state instead of a queue of timestamps per player.
     *
     * <p>A window that appears to have started in the future counts as expired. The world clock can
     * legitimately move backwards ({@code /time set}, or the player changing dimension), and of the
     * two ways to be wrong about that, refusing to reset would lock the player out of breeding XP
     * for as long as the clock stayed behind — silently, and with no way to tell it from a bug.
     *
     * @return {@code true} if an award was available and has now been spent
     */
    private boolean claimBreedAward(long worldTick) {
        final int max = getBreedXpAwardsPerWindow();
        final int windowTicks = getBreedXpAwardWindowTicks();
        if (max <= 0 || windowTicks <= 0) {
            return true; // Gate configured off.
        }
        final long elapsed = worldTick - breedWindowStartTick;
        if (!breedWindowOpen || elapsed < 0 || elapsed >= windowTicks) {
            breedWindowOpen = true;
            breedWindowStartTick = worldTick;
            breedAwardsThisWindow = 0;
            breedCapAnnouncedThisWindow = false;
        }
        if (breedAwardsThisWindow >= max) {
            return false;
        }
        breedAwardsThisWindow++;
        return true;
    }

    /**
     * How many breedings may pay XP per window; {@code 0} or less disables the cap entirely.
     *
     * <p>See {@link #DEFAULT_BREED_XP_AWARDS_PER_WINDOW} for why this gate exists and why it counts
     * payouts rather than breedings.
     */
    public int getBreedXpAwardsPerWindow() {
        final ExperienceConfig experience = experience();
        return experience == null
                ? DEFAULT_BREED_XP_AWARDS_PER_WINDOW
                : experience.getHusbandryBreedXpAwardsPerWindow();
    }

    /** How long one award window lasts, in seconds; {@code 0} or less disables the cap entirely. */
    public int getBreedXpAwardWindowSeconds() {
        final ExperienceConfig experience = experience();
        return experience == null
                ? DEFAULT_BREED_XP_AWARD_WINDOW_SECONDS
                : experience.getHusbandryBreedXpAwardWindowSeconds();
    }

    /** The same window in world ticks, which is the clock {@link #onBreed} is actually measured on. */
    public int getBreedXpAwardWindowTicks() {
        final int seconds = getBreedXpAwardWindowSeconds();
        return seconds <= 0 ? 0 : seconds * Misc.TICK_CONVERSION_FACTOR;
    }

    /**
     * Whether the award cap is switched on at all.
     *
     * <p>Read by {@code /mcstats husbandry}, which renders the cap only when there is one — a line
     * reading "0 per 0s" would be worse than no line.
     */
    public boolean isBreedXpAwardCapped() {
        return getBreedXpAwardsPerWindow() > 0 && getBreedXpAwardWindowTicks() > 0;
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
        return award(getShearXp());
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
        // Herdsman's Call's double-yield half. Expressed as "this roll always wins while the call is
        // sounding" rather than as a separate doubling at each of the four harvest sites, because every
        // verb already routes its bonus through this one method -- so the super gets its effect on all
        // four for free and cannot be wired into three of them by accident. It deliberately does NOT
        // require Bountiful Harvest to be unlocked: the super is its own reward, and gating one
        // sub-skill's effect behind another's rank is the kind of hidden dependency nobody can read
        // off a stats screen.
        if (isHerdsmansCallActive()) {
            return true;
        }
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

    // --- Stage 4: the other three harvest verbs -------------------------------------------------

    /**
     * Credit one hive or bee nest harvested by hand.
     *
     * <p>Flat, and ungated by the breeding table for the same reason {@link #onShear} is: the trigger
     * layer sits on the block's player-interaction path, which no species can reach by accident.
     *
     * @return the XP awarded, or {@code 0} if the verb is priced at nothing
     */
    public float onHiveHarvest() {
        return award(getHiveXp());
    }

    /**
     * Credit one cow milked, or one mooshroom's stew bowled.
     *
     * <p>Both are the same verb and share one payout deliberately: a mooshroom is a cow you can also
     * get soup out of, and pricing the soup differently would make the variant, rather than the act
     * of keeping the animal, the thing the skill rewards.
     *
     * <p><b>Bucket-mobs pay nothing</b> (user ruling, 2026-07-29), reversing the plan's original
     * row. Scooping a fish or an axolotl into a bucket is a <em>capture</em> — the one-time conversion
     * of a wild animal into your property — which is Taming's side of this skill's own verb boundary,
     * not a repeating harvest. It is also the one place the boundary rule and the exploit analysis
     * agreed: an axolotl can be poured out of its bucket and scooped straight back up, so the verb
     * would have been a two-click loop worth 200 XP forever, off one animal and one bucket.
     *
     * @return the XP awarded, or {@code 0} if the verb is priced at nothing
     */
    public float onMilk() {
        return award(getMilkXp());
    }

    /**
     * Credit one armadillo brushed.
     *
     * @return the XP awarded, or {@code 0} if the verb is priced at nothing
     */
    public float onBrush() {
        return award(getBrushXp());
    }

    /** Pay a flat verb, or nothing if it is priced at zero. */
    private float award(float xp) {
        if (xp <= 0) {
            return 0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }

    /**
     * How long one animal must wait before it can pay a harvest award again, in seconds.
     *
     * <p><b>D-H5, widened.</b> The plan called this the <em>milk</em> cooldown, because milking is
     * free and infinitely repeatable on the same cow and would otherwise be the fastest XP in the mod.
     * Bytecode found the brush in exactly the same position and the plan had it filed as safe:
     * {@code brush/armadillo.json} drops a scute with <b>no conditions at all</b>, and
     * {@code ArmadilloEntity#brushScute} neither reads nor resets {@code nextScuteShedCooldown} — that
     * timer governs only the passive shed in {@code mobTick}. So "vanilla's own scute cooldown", the
     * reason the plan rated brushing low-risk, <b>does not exist on the brush path</b>.
     *
     * <p>Which is why this is a <em>harvest</em> cooldown rather than a milk one, and why it covers
     * exactly two of the four harvest verbs. Shearing and hive harvesting are already rate-limited by
     * vanilla and get no cooldown: a just-sheared sheep is worthless until it has eaten its way back
     * to a full coat, and a drained hive needs five levels of bee-pollination time. Milking and
     * brushing are limited by nothing whatsoever, so they are limited here.
     *
     * @return the cooldown in seconds; {@code 0} or less disables it
     */
    public int getHarvestCooldownSeconds() {
        final ExperienceConfig experience = experience();
        if (experience == null) {
            return DEFAULT_HARVEST_COOLDOWN_SECONDS;
        }
        return experience.getHusbandryHarvestCooldownSeconds();
    }

    // --- Sub-skill: Beekeeper -------------------------------------------------------------------

    public boolean canBeekeeper() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_BEEKEEPER)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HUSBANDRY_BEEKEEPER);
    }

    /**
     * Whether this player's hive harvests leave the bees calm.
     *
     * <p>Binary rather than a roll: the value of the effect is being able to stop carrying a campfire
     * and stop planning around one, and a nine-in-ten version of that is worse than not having it —
     * you would still have to build the campfire, for the tenth harvest.
     *
     * <p>Expressed as "you always count as standing over a lit campfire" rather than as a bee-anger
     * suppression, because that is precisely the branch vanilla itself already has, and reusing it
     * covers <b>both</b> ways a harvest angers bees at once: the nearby-bee retargeting and the
     * hive's own emergency release. See {@code BeehiveHarvestMixin}.
     */
    public boolean countsAsShelteredHiveHarvest() {
        return canBeekeeper();
    }

    /**
     * Whether this hive harvest should give up an extra helping of comb or honey.
     *
     * <p>Beekeeper's second, smaller half. It stacks with {@code Bountiful Harvest} rather than
     * replacing it, and that is intentional: Bountiful Harvest is the harvest family's shared
     * across-the-board bonus, while this one is the reward for specialising in bees, so a maxed
     * beekeeper harvesting a full hive should visibly out-yield a maxed generalist.
     */
    public boolean rollBonusHoney() {
        return canBeekeeper()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HUSBANDRY_BEEKEEPER, mmoPlayer);
    }

    // --- Stage 5: Selective Breeding -------------------------------------------------------------

    public boolean canSelectiveBreeding() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_SELECTIVE_BREEDING)
                && Permissions.isSubSkillEnabled(getPlayer(),
                        SubSkillType.HUSBANDRY_SELECTIVE_BREEDING);
    }

    /**
     * How far toward the best possible value this player's offspring are nudged, as {@code 0.0}–
     * {@link #HARD_MAX_STAT_BIAS}.
     *
     * @return the bias fraction, or {@code 0} when Selective Breeding is locked
     */
    public double getStatBias() {
        if (!canSelectiveBreeding()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final double max = advanced == null
                ? DEFAULT_MAX_STAT_BIAS
                : advanced.getMaxSelectiveBreedingBias();
        if (max <= 0) {
            return 0.0;
        }
        final int maxBonusLevel = advanced == null
                ? 0
                : advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_SELECTIVE_BREEDING);
        return Math.min(HARD_MAX_STAT_BIAS, scaleToLevel(max, maxBonusLevel));
    }

    /**
     * Nudge one rolled offspring stat toward the best value the species allows.
     *
     * <p>Applied to the <em>outcome</em> of vanilla's own inheritance roll rather than to the dice
     * that produced it, which is what keeps the sub-skill honest across every attribute it touches:
     * vanilla rolls a bell curve around the midpoint of the two parents, widened by their spread, and
     * this shifts that result a fraction of the remaining distance to the ceiling. So good parents
     * still matter — the bias moves you along the range, it does not replace the range — and the
     * result can never exceed what the species permits.
     *
     * <p><b>It can only ever improve a foal, never worsen one</b>, and at bias {@code 0} it is exactly
     * the identity. That matters because the same code path runs for every horse bred in the world,
     * including by players who have not unlocked the sub-skill.
     *
     * @param rolled vanilla's rolled value
     * @param min    the lowest value the attribute may take
     * @param max    the highest value the attribute may take
     * @return the biased value, clamped into {@code [min, max]}
     */
    public double applyStatBias(double rolled, double min, double max) {
        if (max <= min) {
            return rolled; // Degenerate range; nothing to bias toward.
        }
        final double bias = getStatBias();
        if (bias <= 0) {
            return rolled;
        }
        final double clamped = Math.min(max, Math.max(min, rolled));
        return clamped + bias * (max - clamped);
    }

    // --- Stage 5: Brood --------------------------------------------------------------------------

    public boolean canBrood() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_BROOD)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.HUSBANDRY_BROOD);
    }

    /**
     * Whether a thrown egg that vanilla was about to waste should hatch anyway.
     *
     * <p>Layered on top of vanilla's 1-in-8 rather than replacing it, so the shipped
     * {@code ChanceMax} reads as "how often Brood rescues an egg that would have broken" and the
     * effective hatch rate is {@code 12.5% + chance × 87.5%}. Replacing the roll outright would have
     * made a configured 10 % a <em>downgrade</em> on vanilla, which is the kind of knob nobody
     * notices is backwards.
     *
     * <p><b>Deliberately pays no XP and marks no chick.</b> Chickens lay eggs on a passive timer —
     * {@code ChickenEntity.eggLayTime} is ticked in {@code tickMovement} — so a hopper under a coop
     * is fully AFK income, which is exactly why stage 0 priced no egg verb. Brood is a yield
     * sub-skill only. The chick is also not given a bred-by marker, so raising it pays nobody: it was
     * hatched, not bred, and a marker here would have quietly turned an AFK egg farm into a raise-XP
     * farm twenty minutes later.
     */
    public boolean rollEggHatch() {
        return canBrood()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HUSBANDRY_BROOD, mmoPlayer);
    }

    /**
     * Whether a hatching egg should yield vanilla's rare four chicks rather than one.
     *
     * <p>The second of this sub-skill's two rolls, so it is scaled by hand off
     * {@link #getMultiChickChance} rather than through the sub-skill's own probability —
     * {@code ProbabilityUtil} keys its chance off the {@link SubSkillType}, so only one effect per
     * sub-skill can live there. Same split Accelerated Growth and Bountiful Harvest already make.
     */
    public boolean rollMultipleChicks() {
        final double chance = getMultiChickChance();
        return chance > 0
                && ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.HUSBANDRY, mmoPlayer,
                        chance);
    }

    /**
     * This player's current chance that a hatch yields a full clutch, in percent.
     *
     * @return {@code 0}–100, or {@code 0} when Brood is locked
     */
    public double getMultiChickChance() {
        if (!canBrood()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final double max = advanced == null
                ? DEFAULT_MULTI_CHICK_CHANCE
                : advanced.getBroodMultiChickChance();
        if (max <= 0) {
            return 0.0;
        }
        final int maxBonusLevel = advanced == null
                ? 0
                : advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_BROOD);
        return Math.min(100.0, scaleToLevel(max, maxBonusLevel));
    }

    // --- Stage 5: Hidden Bounty -----------------------------------------------------------------

    public boolean canHiddenBounty() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_HIDDEN_BOUNTY)
                && Permissions.isSubSkillEnabled(getPlayer(),
                        SubSkillType.HUSBANDRY_HIDDEN_BOUNTY);
    }

    /** Whether this harvest gets to look at the treasure table at all. */
    public boolean rollHiddenBounty() {
        return canHiddenBounty()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.HUSBANDRY_HIDDEN_BOUNTY,
                        mmoPlayer);
    }

    /**
     * Pick the treasure a harvest turned up, if any.
     *
     * <p>MC-free and fully injectable, the same shape as {@code HerbalismManager#rollHylianLuck} and
     * {@code FishingManager#rollFishingTreasure}: both random draws arrive from the caller, so the
     * whole selection is unit-testable and the listener owns only the item spawn.
     *
     * <p>Walks the candidates in config order and returns the first whose level requirement this
     * player has reached <em>and</em> whose own {@code Drop_Chance} rolls. Config order is therefore
     * load-bearing — a common treasure listed first makes every rarer one unreachable — which is
     * stated in {@code treasures.yml} beside the table.
     *
     * @param candidates  the treasures for the harvested verb, in config order
     * @param mainRollWon whether {@link #rollHiddenBounty()} succeeded; a failure returns empty
     *                    without touching the table
     * @param staticRoll  evaluates one treasure's percentage {@code Drop_Chance}
     * @return the winning treasure, or empty
     */
    public Optional<HusbandryTreasure> selectHiddenBounty(List<HusbandryTreasure> candidates,
            boolean mainRollWon, DoublePredicate staticRoll) {
        if (!mainRollWon || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        final int level = getSkillLevel();
        for (HusbandryTreasure treasure : candidates) {
            if (treasure.getDropLevel() <= level && staticRoll.test(treasure.getDropChance())) {
                return Optional.of(treasure);
            }
        }
        return Optional.empty();
    }

    /**
     * Credit the small XP bonus a {@code Hidden Bounty} find carries, on top of the verb's own payout.
     *
     * <p>Deliberately small and per-treasure. The find <em>is</em> the reward; a treasure that also paid
     * a verb's worth of XP would make Hidden Bounty the reason to harvest rather than a bonus for
     * having done so.
     *
     * @param xp the treasure's configured XP
     * @return the XP awarded, or {@code 0} for a treasure priced at nothing
     */
    public float onHiddenBountyFound(int xp) {
        return award(atLeastZero(xp));
    }

    // --- Stage 6: Herdsman's Call (the super ability) -------------------------------------------

    public boolean canHerdsmansCall() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.HUSBANDRY_HERDSMANS_CALL)
                && Permissions.isSubSkillEnabled(getPlayer(),
                        SubSkillType.HUSBANDRY_HERDSMANS_CALL);
    }

    /** Whether the call is sounding right now. */
    public boolean isHerdsmansCallActive() {
        return mmoPlayer.getAbilityMode(SuperAbilityType.HERDSMANS_CALL);
    }

    /**
     * How far the call reaches, in blocks; {@code 0} when it is not active.
     *
     * <p>Clamped to {@link #HARD_MAX_HERD_RADIUS}. Returning {@code 0} while inactive is what keeps the
     * per-tick sweep off the hot path entirely rather than having it scan and discard.
     */
    public double getHerdRadius() {
        return isHerdsmansCallActive() ? getMaxHerdRadius() : 0.0;
    }

    /**
     * The call's configured maximum reach in blocks, regardless of whether it is sounding.
     *
     * <p>Separate from {@link #getHerdRadius()} because the two answer different questions:
     * {@code /mcstats} wants "how far this reaches", while the per-tick sweep wants "how far to scan
     * right now", and the sweep's answer must be {@code 0} while the ability is idle or it would scan
     * and discard twenty times a second forever.
     */
    public double getMaxHerdRadius() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final double radius = advanced == null
                ? DEFAULT_HERD_RADIUS
                : advanced.getHerdsmansCallRadius();
        return Math.min(HARD_MAX_HERD_RADIUS, Math.max(0.0, radius));
    }

    /** The configured floor on the call's duration, in ticks; never less than one tick. */
    public int getHerdsmansCallDurationTicks() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null
                ? DEFAULT_HERDSMANS_CALL_DURATION_TICKS
                : Math.max(1, advanced.getHerdsmansCallDurationTicks());
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
