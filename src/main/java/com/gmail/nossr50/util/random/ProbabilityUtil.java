package com.gmail.nossr50.util.random;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.Permissions;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Skill RNG helper — decides whether a subskill "procs".
 *
 * <p>PORT Phase 10.2: the Bukkit-typed {@code (Player, ...)} overloads that only delegated to the
 * {@link McMMOPlayer} versions via {@code UserManager.getPlayer(player)} were dropped — ported call
 * sites already hold an {@link McMMOPlayer}. Config reads go through {@link McMMOMod#getAdvancedConfig()}
 * (was {@code mcMMO.p.getAdvancedConfig()}).
 *
 * <p>PORT Phase 10.2: the {@code SubSkillEvent} fire in {@link #getSkillProbability} and
 * {@link #isNonRNGSkillActivationSuccessful} was an API hook that let <em>other Bukkit plugins</em>
 * cancel or scale a proc. Singleplayer has no external listeners and mcMMO itself never subscribes to
 * it, so the fire is a no-op here and is dropped (see the scope-reduction decision to cut the
 * plugin-API surface). Re-introduce on the internal event bus only if an in-mod listener ever needs it.
 */
public class ProbabilityUtil {
    public static final @NotNull DecimalFormat percent = new DecimalFormat("##0.00%",
            DecimalFormatSymbols.getInstance(Locale.US));
    public static final double LUCKY_MODIFIER = 1.333D;

    /**
     * Return a chance of success as a double representing a "percentage".
     *
     * @param mmoPlayer target player
     * @param subSkillType target subskill
     * @param isLucky whether to apply luck modifiers
     * @return "percentage" representation of success
     */
    public static double chanceOfSuccessPercentage(@Nullable McMMOPlayer mmoPlayer,
            @NotNull SubSkillType subSkillType,
            boolean isLucky) {
        final Probability probability = getSubSkillProbability(subSkillType, mmoPlayer);
        //Probability values are on a 0-1 scale and need to be "transformed" into a 1-100 scale
        double percentageValue = probability.getValue(); //Doesn't need to be scaled

        //Apply lucky modifier
        if (isLucky) {
            percentageValue *= LUCKY_MODIFIER;
        }

        return percentageValue;
    }

    /**
     * Return a chance of success as a double representing a "percentage".
     *
     * @param probability the probability of success
     * @param isLucky whether to apply luck modifiers
     * @return a double as a "percentage" representation of success
     */
    public static double chanceOfSuccessPercentage(@NotNull Probability probability,
            boolean isLucky) {
        //Probability values are on a 0-1 scale and need to be "transformed" into a 1-100 scale
        double percentageValue = probability.getValue();

        //Apply lucky modifier
        if (isLucky) {
            percentageValue *= LUCKY_MODIFIER;
        }

        return percentageValue;
    }

    @VisibleForTesting
    static Probability getStaticRandomChance(@NotNull SubSkillType subSkillType)
            throws InvalidStaticChance {
        return switch (subSkillType) {
            case AXES_ARMOR_IMPACT ->
                    Probability.ofPercent(McMMOMod.getAdvancedConfig().getImpactChance());
            case AXES_GREATER_IMPACT ->
                    Probability.ofPercent(McMMOMod.getAdvancedConfig().getGreaterImpactChance());
            case TAMING_FAST_FOOD_SERVICE ->
                    Probability.ofPercent(McMMOMod.getAdvancedConfig().getFastFoodChance());
            default -> throw new InvalidStaticChance();
        };
    }

    static SkillProbabilityType getProbabilityType(@NotNull SubSkillType subSkillType) {
        SkillProbabilityType skillProbabilityType = SkillProbabilityType.DYNAMIC_CONFIGURABLE;

        if (subSkillType == SubSkillType.TAMING_FAST_FOOD_SERVICE
                || subSkillType == SubSkillType.AXES_ARMOR_IMPACT
                || subSkillType == SubSkillType.AXES_GREATER_IMPACT) {
            skillProbabilityType = SkillProbabilityType.STATIC_CONFIGURABLE;
        }

        return skillProbabilityType;
    }

    private static @NotNull Probability ofSubSkill(@Nullable McMMOPlayer mmoPlayer,
            @NotNull SubSkillType subSkillType) {
        switch (getProbabilityType(subSkillType)) {
            case DYNAMIC_CONFIGURABLE:
                double probabilityCeiling;
                double skillLevel;
                double maxBonusLevel; // If a skill level is equal to the cap, it has the full probability

                if (mmoPlayer != null) {
                    skillLevel = mmoPlayer.getSkillLevel(subSkillType.getParentSkill());
                } else {
                    skillLevel = 0;
                }

                //Probability ceiling is configurable in this type
                probabilityCeiling = McMMOMod.getAdvancedConfig()
                        .getMaximumProbability(subSkillType);
                //The xCeiling is configurable in this type
                maxBonusLevel = McMMOMod.getAdvancedConfig().getMaxBonusLevel(subSkillType);
                return calculateCurrentSkillProbability(skillLevel, 0, probabilityCeiling,
                        maxBonusLevel);
            case STATIC_CONFIGURABLE:
                try {
                    return getStaticRandomChance(subSkillType);
                } catch (InvalidStaticChance invalidStaticChance) {
                    throw new RuntimeException(invalidStaticChance);
                }
            default:
                throw new IllegalStateException(
                        "No case in switch statement for Skill Probability Type!");
        }
    }

    /**
     * Skill RNG check for a specific {@link SubSkillType}. Determines where the RNG values come from
     * (static vs dynamic config), applies the lucky modifier, and rolls the resulting
     * {@link Probability}.
     *
     * @param subSkillType target subskill
     * @param mmoPlayer target player can be null (null players are given odds equivalent to a
     * player with no levels or luck)
     * @return true if the Skill RNG succeeds, false if it fails
     */
    public static boolean isSkillRNGSuccessful(@NotNull SubSkillType subSkillType,
            @Nullable McMMOPlayer mmoPlayer) {
        final Probability probability = getSkillProbability(subSkillType, mmoPlayer);

        //Luck
        boolean isLucky = mmoPlayer != null && Permissions.lucky(mmoPlayer.getPlayer(),
                subSkillType.getParentSkill());

        if (isLucky) {
            return probability.evaluate(LUCKY_MODIFIER);
        } else {
            return probability.evaluate();
        }
    }

    /**
     * Skill RNG evaluation that additionally applies a probability multiplier after any lucky
     * modifier, affecting the final result.
     *
     * @param subSkillType target subskill
     * @param mmoPlayer target player can be null (null players are given odds equivalent to a
     * player with no levels or luck)
     * @return true if the Skill RNG succeeds, false if it fails
     */
    public static boolean isSkillRNGSuccessful(@NotNull SubSkillType subSkillType,
            @Nullable McMMOPlayer mmoPlayer,
            double probabilityMultiplier) {
        final Probability probability = getSkillProbability(subSkillType, mmoPlayer);

        //Luck
        boolean isLucky = mmoPlayer != null && Permissions.lucky(mmoPlayer.getPlayer(),
                subSkillType.getParentSkill());

        if (isLucky) {
            return probability.evaluate(LUCKY_MODIFIER, probabilityMultiplier);
        } else {
            return probability.evaluate();
        }
    }

    /**
     * Returns the {@link Probability} for a specific {@link SubSkillType} for a specific
     * {@link McMMOPlayer}. This does not take into account perks such as lucky for the player. Null
     * players will be treated as zero skill players.
     *
     * @param subSkillType the target subskill
     * @param mmoPlayer the target player can be null (null players have the worst odds)
     * @return the probability for this skill
     */
    public static Probability getSkillProbability(@NotNull SubSkillType subSkillType,
            @Nullable McMMOPlayer mmoPlayer) {
        // Process probability
        // PORT Phase 10.2: legacy fired a cancellable/mutable SubSkillEvent here for other plugins;
        // dropped in singleplayer (no external listeners) — see class javadoc.
        return getSubSkillProbability(subSkillType, mmoPlayer);
    }

    /**
     * Static-value RNG check, which can be influenced by a player's Luck.
     *
     * @param primarySkillType the related primary skill
     * @param mmoPlayer the target player can be null (null players have the worst odds)
     * @param probabilityPercentage the probability of this player succeeding in "percentage" format
     * (0-100 inclusive)
     * @return true if the RNG succeeds, false if it fails
     */
    public static boolean isStaticSkillRNGSuccessful(@NotNull PrimarySkillType primarySkillType,
            @Nullable McMMOPlayer mmoPlayer, double probabilityPercentage) {
        //Grab a probability converted from a "percentage" value
        final Probability probability = Probability.ofPercent(probabilityPercentage);

        return isStaticSkillRNGSuccessful(primarySkillType, mmoPlayer, probability);
    }

    /**
     * Static-value RNG check, which can be influenced by a mmoPlayer's Luck.
     *
     * @param primarySkillType the related primary skill
     * @param mmoPlayer the target mmoPlayer can be null (null players have the worst odds)
     * @param probability the probability of this mmoPlayer succeeding
     * @return true if the RNG succeeds, false if it fails
     */
    public static boolean isStaticSkillRNGSuccessful(@NotNull PrimarySkillType primarySkillType,
            @Nullable McMMOPlayer mmoPlayer, @NotNull Probability probability) {
        boolean isLucky =
                mmoPlayer != null && Permissions.lucky(mmoPlayer.getPlayer(), primarySkillType);

        if (isLucky) {
            return probability.evaluate(LUCKY_MODIFIER);
        } else {
            return probability.evaluate();
        }
    }

    /**
     * Skills activate without RNG. Legacy allowed other plugins to prevent that activation via
     * SubSkillEvent; singleplayer has no such listeners, so activation always succeeds.
     *
     * @param subSkillType target subskill
     * @param mmoPlayer target player
     * @return true if the skill succeeds
     */
    public static boolean isNonRNGSkillActivationSuccessful(@NotNull SubSkillType subSkillType,
            @NotNull McMMOPlayer mmoPlayer) {
        // PORT Phase 10.2: legacy returned !callSubSkillEvent(...).isCancelled(); no external
        // listeners in singleplayer, so this is unconditionally true — see class javadoc.
        return true;
    }

    /**
     * Retrieves the {@link Probability} of success for a specified {@link SubSkillType} for a given
     * {@link McMMOPlayer}.
     *
     * @param subSkillType The targeted subskill.
     * @param mmoPlayer The player in question. If null, the method treats it as a player with no
     * levels or luck and calculates the probability accordingly.
     * @return The probability that the specified skill will succeed.
     */
    public static @NotNull Probability getSubSkillProbability(@NotNull SubSkillType subSkillType,
            @Nullable McMMOPlayer mmoPlayer) {
        return ProbabilityUtil.ofSubSkill(mmoPlayer, subSkillType);
    }

    public static @NotNull String[] getRNGDisplayValues(@Nullable McMMOPlayer mmoPlayer,
            @NotNull SubSkillType subSkill) {
        double firstValue = chanceOfSuccessPercentage(mmoPlayer, subSkill, false);
        double secondValue = chanceOfSuccessPercentage(mmoPlayer, subSkill, true);

        return new String[]{percent.format(firstValue), percent.format(secondValue)};
    }

    public static @NotNull String[] getRNGDisplayValues(@NotNull Probability probability) {
        double firstValue = chanceOfSuccessPercentage(probability, false);
        double secondValue = chanceOfSuccessPercentage(probability, true);

        return new String[]{percent.format(firstValue), percent.format(secondValue)};
    }

    /**
     * Helper function to calculate what probability a given skill has at a certain level
     *
     * @param skillLevel the skill level currently between the floor and the ceiling
     * @param floor the minimum odds this skill can have
     * @param ceiling the maximum odds this skill can have
     * @param maxBonusLevel the maximum level this skill can have to reach the ceiling
     * @return the probability of success for this skill at this level
     */
    public static Probability calculateCurrentSkillProbability(double skillLevel, double floor,
            double ceiling,
            double maxBonusLevel) {
        // The odds of success are between the value of the floor and the value of the ceiling.
        // If the skill has a maxBonusLevel of 500 on this skill, then at skill level 500 you would have the full odds,
        // at skill level 250 it would be half odds.

        if (skillLevel >= maxBonusLevel || maxBonusLevel <= 0) {
            // Avoid divide by zero bugs
            // Max benefit has been reached, should always succeed
            return Probability.ofPercent(ceiling);
        }

        double odds = ((skillLevel / maxBonusLevel) * ceiling);

        // make sure the odds aren't lower or higher than the floor or ceiling
        return Probability.ofPercent(Math.min(Math.max(floor, odds), ceiling));
    }
}
