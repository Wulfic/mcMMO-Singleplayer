package com.gmail.nossr50.skills.husbandry;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
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
