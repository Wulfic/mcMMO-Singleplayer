package com.gmail.nossr50.config.experience;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.experience.FormulaType;
import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionStage;
import com.gmail.nossr50.skills.agility.Medium;
import com.gmail.nossr50.skills.agility.MovementXpSettings;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.skills.stealth.StealthXpSettings;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.util.text.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * mcMMO's XP tuning config ({@code experience.yml}), ported onto {@link ConfigLoader}.
 *
 * <p><b>Port note (singleplayer):</b> the legacy class was a self-initializing singleton that
 * enumerated Bukkit's {@code Material.values()} at load time to pre-compute per-block XP. That is
 * impossible in the Fabric port — item/block registries only populate after MC bootstrap and cannot
 * be enumerated at config-load time (see {@code platform/Materials}). Instead the per-block XP map
 * is built by walking the {@code Experience_Values.<Skill>} sections directly, so it needs no
 * registry and stays unit-testable. Lookups are keyed on the config-friendly material/entity
 * strings ({@code ConfigStringUtils}) that callers derive from a vanilla registry path, replacing
 * the old {@code Material}/{@code EntityType}/{@code Block}/{@code BlockState}/{@code BlockData}
 * overloads.
 *
 * <p><b>Deferred (re-add with their Phase 10 skills):</b> {@code getPotionXP(PotionStage)} +
 * alchemy validation (alchemy not ported), {@code getRepairXP(MaterialType)} (repair not ported).
 * The boss-bar colour/style getters now ship as Minecraft-free String reads
 * ({@link #getExperienceBarColorName}/{@link #getExperienceBarStyleName}); the vanilla
 * {@code BossBar.Color}/{@code Style} mapping lives in
 * {@link com.gmail.nossr50.util.experience.ExperienceBarWrapper}.
 */
public class ExperienceConfig extends ConfigLoader {

    /** Per-skill map of config-material-string -> XP, built from the {@code Experience_Values} tree. */
    private final Map<PrimarySkillType, Map<String, Integer>> blockExperienceMap =
            new EnumMap<>(PrimarySkillType.class);

    public ExperienceConfig(Path dataFolder) {
        super("experience.yml", dataFolder);
        loadKeys();
        validateKeys();
    }

    @Override
    protected void loadKeys() {
        blockExperienceMap.clear();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final Map<String, Integer> experienceMap = new HashMap<>();
            blockExperienceMap.put(skill, experienceMap);

            final String sectionPath =
                    "Experience_Values." + StringUtils.getCapitalized(skill.toString());
            final YamlConfiguration section = config.getConfigurationSection(sectionPath);
            if (section == null) {
                continue;
            }

            for (String key : section.getKeys(false)) {
                // Only direct scalar children are per-block XP entries; nested sections
                // (e.g. Combat.Multiplier, Taming.Animal_Taming) are addressed by their own getters.
                if (section.isConfigurationSection(key)) {
                    continue;
                }
                final int xp = section.getInt(key, 0);
                if (xp > 0) {
                    experienceMap.put(key, xp);
                }
            }
        }
    }

    public boolean isEarlyGameBoostEnabled() {
        return config.getBoolean("EarlyGameBoost.Enabled", true);
    }

    /*
     * EXPLOIT TOGGLES
     */
    public boolean isSnowExploitPrevented() {
        return config.getBoolean("ExploitFix.SnowGolemExcavation", true);
    }

    public boolean isEndermanEndermiteFarmingPrevented() {
        return config.getBoolean("ExploitFix.EndermanEndermiteFarms", true);
    }

    public boolean isPistonCheatingPrevented() {
        return config.getBoolean("ExploitFix.PistonCheating", true);
    }

    public boolean isPistonExploitPrevented() {
        return config.getBoolean("ExploitFix.Pistons", false);
    }

    public boolean allowUnsafeEnchantments() {
        return config.getBoolean("ExploitFix.UnsafeEnchantments", false);
    }

    public boolean isCOTWBreedingPrevented() {
        return config.getBoolean("ExploitFix.COTWBreeding", true);
    }

    public boolean isNPCInteractionPrevented() {
        return config.getBoolean("ExploitFix.PreventPluginNPCInteraction", true);
    }

    public boolean isArmorStandInteractionPrevented() {
        return config.getBoolean("ExploitFix.PreventArmorStandInteraction", true);
    }

    public boolean isMannequinInteractionPrevented() {
        return config.getBoolean("ExploitFix.PreventMannequinInteraction", true);
    }

    public boolean isFishingExploitingPrevented() {
        return config.getBoolean("ExploitFix.Fishing", true);
    }

    public int getFishingExploitingOptionMoveRange() {
        return config.getInt("Fishing_ExploitFix_Options.MoveRange", 3);
    }

    public int getFishingExploitingOptionOverFishLimit() {
        return config.getInt("Fishing_ExploitFix_Options.OverFishLimit", 10);
    }

    public boolean isAgilityExploitingPrevented() {
        return config.getBoolean("ExploitFix.Agility", true);
    }

    public boolean isTreeFellerXPReduced() {
        return config.getBoolean("ExploitFix.TreeFellerReducedXP", true);
    }

    public boolean preventStoneLavaFarming() {
        return config.getBoolean("ExploitFix.LavaStoneAndCobbleFarming", true);
    }

    public boolean limitXPOnTallPlants() {
        return config.getBoolean("ExploitFix.LimitTallPlantFarming", true);
    }

    /**
     * Whether hand-placed blocks are tracked and denied gathering rewards when re-mined (the §A/K9
     * {@code PlacedBlockTracker}).
     *
     * <p>This is the single biggest anti-farm gate in the mod and it had <b>no config key at all</b>
     * until GitHub #9 — turning it off restores the pre-K9 behaviour, where placing a stack of ore
     * and re-mining it pays full XP every time. There is no legacy counterpart: upstream's
     * {@code UserBlockTracker} is unconditional.
     */
    public boolean isPlacedBlockTrackingEnabled() {
        return config.getBoolean("ExploitFix.PlacedBlocks", true);
    }

    /**
     * The combat-XP multiplier for a mob with this spawn origin — legacy's per-{@code MobMetaFlagType}
     * scaling, which shipped in {@code experience.yml} from the start and was read by nothing until
     * GitHub #9.
     *
     * <p>All four keys default to <b>0</b> except breeding, so with this wired a spawner grinder
     * stops paying combat XP entirely — which is what the file has always claimed. The markers were
     * already being stamped on every mob by {@code EntityTypeSpawnOriginMixin}; only this lookup was
     * missing, so {@code CombatUtils#processCombatXP}'s note that "the multipliers do nothing yet"
     * had outlived its own reason (it blamed the unported {@code MobMetaFlagType}, which
     * {@link MobOrigin} replaced).
     *
     * <p>{@link MobOrigin#UNKNOWN} deliberately pays in full rather than failing closed, unlike the
     * mastery gate. That asymmetry is {@link MobOrigin}'s own ruling — "XP is a rate; mastery is
     * permanent, and only the permanent thing needs the strict gate" — and the only way to see an
     * {@code UNKNOWN} today is to downgrade the mod, where silently zeroing all combat XP would be a
     * far worse failure than paying for a mob whose marker we cannot read.
     */
    public double getMobOriginXpMultiplier(@NotNull MobOrigin origin) {
        return switch (origin) {
            case NATURAL, UNKNOWN -> 1.0D;
            case SPAWNER -> getSpawnedMobXpMultiplier();
            case BRED -> getBredMobXpMultiplier();
            case PLAYER_PLACED -> getEggXpMultiplier();
            case STRUCTURE -> getNetherPortalXpMultiplier();
        };
    }

    public boolean useCombatHPCeiling() {
        return config.getBoolean("ExploitFix.Combat.XPCeiling.Enabled", true);
    }

    /**
     * The creditable-damage ceiling for a single hit.
     *
     * <p>⚠️ <b>Reads {@code Damage_Limit}, not legacy's {@code HP_Modifier_Limit}.</b> This port's
     * hand-authored {@code experience.yml} has always shipped the key as {@code Damage_Limit} —
     * legacy publishes no {@code experience.yml} resource at all, so there was nothing to copy — and
     * until 2026-08-05 this getter read the legacy name. The two never met: the shipped key was read
     * by nobody and the getter fell back to its hard-coded {@code 100} forever, so <em>editing the
     * documented key did nothing</em>. The shipped name wins because it is the one on every existing
     * config on disk (changing the file instead would strand every player's tuning) and because it
     * is the accurate one — this clamps damage, it modifies no HP.
     *
     * <p>Pinned by {@code ExperienceConfigKeyAgreementTest}, which drives every ExploitFix getter
     * through a non-default value written at the <em>shipped</em> path.
     */
    public int getCombatHPCeiling() {
        return config.getInt("ExploitFix.Combat.XPCeiling.Damage_Limit", 100);
    }

    /*
     * FORMULA SETTINGS
     */
    public FormulaType getFormulaType() {
        return FormulaType.getFormulaType(config.getString("Experience_Formula.Curve"));
    }

    public boolean getCumulativeCurveEnabled() {
        return config.getBoolean("Experience_Formula.Cumulative_Curve", false);
    }

    public double getMultiplier(FormulaType type) {
        return config.getDouble("Experience_Formula." + StringUtils.getCapitalized(type.toString())
                + "_Values.multiplier");
    }

    public int getBase(FormulaType type) {
        return config.getInt("Experience_Formula." + StringUtils.getCapitalized(type.toString())
                + "_Values.base");
    }

    public double getExponent(FormulaType type) {
        return config.getDouble("Experience_Formula." + StringUtils.getCapitalized(type.toString())
                + "_Values.exponent");
    }

    public double getExperienceGainsGlobalMultiplier() {
        return config.getDouble("Experience_Formula.Multiplier.Global", 1.0);
    }

    public void setExperienceGainsGlobalMultiplier(double value) {
        config.set("Experience_Formula.Multiplier.Global", value);
    }

    public double getPlayerVersusPlayerXP() {
        return config.getDouble("Experience_Formula.Multiplier.PVP", 1.0);
    }

    public double getSpawnedMobXpMultiplier() {
        return config.getDouble("Experience_Formula.Mobspawners.Multiplier", 0.0);
    }

    public double getEggXpMultiplier() {
        return config.getDouble("Experience_Formula.Eggs.Multiplier", 0.0);
    }

    public double getTamedMobXpMultiplier() {
        return config.getDouble("Experience_Formula.Player_Tamed.Multiplier", 0.0);
    }

    public double getNetherPortalXpMultiplier() {
        return config.getDouble("Experience_Formula.Nether_Portal.Multiplier", 0.0);
    }

    public double getBredMobXpMultiplier() {
        return config.getDouble("Experience_Formula.Breeding.Multiplier", 1.0);
    }

    public double getFormulaSkillModifier(PrimarySkillType skill) {
        return config.getDouble("Experience_Formula.Skill_Multiplier."
                + StringUtils.getCapitalized(skill.toString()), 1);
    }

    public double getCustomXpPerkBoost() {
        return config.getDouble("Experience_Formula.Custom_XP_Perk.Boost", 1.25);
    }

    /* Diminished Returns */
    public float getDiminishedReturnsCap() {
        return (float) config.getDouble("Diminished_Returns.Guaranteed_Minimum_Percentage", 0.05D);
    }

    public boolean getDiminishedReturnsEnabled() {
        return config.getBoolean("Diminished_Returns.Enabled", false);
    }

    public int getDiminishedReturnsThreshold(PrimarySkillType skill) {
        return config.getInt("Diminished_Returns.Threshold."
                + StringUtils.getCapitalized(skill.toString()), 20000);
    }

    public int getDiminishedReturnsTimeInterval() {
        return config.getInt("Diminished_Returns.Time_Interval", 10);
    }

    /* Conversion */
    public double getExpModifier() {
        return config.getDouble("Conversion.Exp_Modifier", 1);
    }

    /*
     * XP SETTINGS
     */
    public boolean getExperienceGainsPlayerVersusPlayerEnabled() {
        return config.getBoolean("Experience_Values.PVP.Rewards", true);
    }

    /* Combat XP Multipliers — keyed by config-entity string (see ConfigStringUtils). */
    public double getCombatXP(String entityConfigString) {
        return config.getDouble("Experience_Values.Combat.Multiplier." + entityConfigString);
    }

    public double getAnimalsXP(String entityConfigString) {
        return config.getDouble("Experience_Values.Combat.Multiplier." + entityConfigString,
                getAnimalsXP());
    }

    public double getAnimalsXP() {
        return config.getDouble("Experience_Values.Combat.Multiplier.Animals", 1.0);
    }

    public boolean hasCombatXP(String entityConfigString) {
        return config.contains("Experience_Values.Combat.Multiplier." + entityConfigString);
    }

    /* Materials — keyed by config-material string (see ConfigStringUtils). */
    public int getXp(PrimarySkillType skill, String materialConfigString) {
        final Map<String, Integer> skillMap = blockExperienceMap.get(skill);
        return skillMap == null ? 0 : skillMap.getOrDefault(materialConfigString, 0);
    }

    public boolean doesBlockGiveSkillXP(PrimarySkillType skill, String materialConfigString) {
        return getXp(skill, materialConfigString) > 0;
    }

    /* Experience bars — boolean toggles kept; boss-bar Color/Style getters deferred to Phase 10. */
    public boolean isExperienceBarsEnabled() {
        return config.getBoolean("Experience_Bars.Enable", true);
    }

    public boolean isPassiveGainsExperienceBarsEnabled() {
        return config.getBoolean("Experience_Bars.Update.Passive", true);
    }

    public boolean isExperienceBarEnabled(PrimarySkillType primarySkillType) {
        return config.getBoolean("Experience_Bars."
                + StringUtils.getCapitalized(primarySkillType.toString()) + ".Enable", true);
    }

    public boolean getDoExperienceBarsAlwaysUpdateTitle() {
        return config.getBoolean(
                "Experience_Bars.ThisMayCauseLag.AlwaysUpdateTitlesWhenXPIsGained.Enable", false)
                || getAddExtraDetails();
    }

    public boolean getAddExtraDetails() {
        return config.getBoolean(
                "Experience_Bars.ThisMayCauseLag.AlwaysUpdateTitlesWhenXPIsGained.ExtraDetails",
                false);
    }

    /**
     * The bar colour for a skill as the raw config name (a legacy Bukkit {@code BarColor} value such
     * as {@code YELLOW}). Kept as a String so this config class stays Minecraft-free; the mapping to
     * the vanilla {@code BossBar.Color} lives in {@link
     * com.gmail.nossr50.util.experience.ExperienceBarWrapper}. Defaults to {@code PINK}.
     */
    public String getExperienceBarColorName(PrimarySkillType primarySkillType) {
        return config.getString("Experience_Bars."
                + StringUtils.getCapitalized(primarySkillType.toString()) + ".Color", "PINK");
    }

    /**
     * The bar style for a skill as the raw config name (a legacy Bukkit {@code BarStyle} value such
     * as {@code SEGMENTED_6}). Mapped to the vanilla {@code BossBar.Style} by {@link
     * com.gmail.nossr50.util.experience.ExperienceBarWrapper}. Defaults to {@code SEGMENTED_6}.
     */
    public String getExperienceBarStyleName(PrimarySkillType primarySkillType) {
        return config.getString("Experience_Bars."
                + StringUtils.getCapitalized(primarySkillType.toString()) + ".BarStyle",
                "SEGMENTED_6");
    }

    /**
     * Seconds of inactivity in a skill before its XP bar fades. Legacy hard-coded 3; exposed as a
     * config knob here and defaulted to 10 (the singleplayer default requested for this port).
     */
    public int getExperienceBarHideDelaySeconds() {
        return config.getInt("Experience_Bars.Hide_Delay_Seconds", 10);
    }

    /**
     * How many XP bars may be on screen at once, or {@code 0} for no limit.
     *
     * <p>Boss bars stack downward from the top of the screen and nothing in vanilla bounds them, so
     * without a cap a player doing several things at once — sprinting through a forest while mining,
     * say — ends up reading the world through a stack of progress bars. When a new skill's bar
     * appears and the cap is already met, the least recently trained one is hidden rather than the
     * new one suppressed: the bar you want to see is the skill you just used.
     */
    public int getMaxVisibleExperienceBars() {
        return config.getInt("Experience_Bars.Max_Visible", 3);
    }

    /* Agility */
    public int getDodgeXPModifier() {
        return config.getInt("Experience_Values.Agility.Dodge", 120);
    }

    public int getRollXPModifier() {
        return config.getInt("Experience_Values.Agility.Roll", 80);
    }

    public int getFallXPModifier() {
        return config.getInt("Experience_Values.Agility.Fall", 120);
    }

    public double getFeatherFallXPModifier() {
        return config.getDouble("Experience_Values.Agility.FeatherFall_Multiplier", 2.0);
    }

    /**
     * Agility movement XP paid per second of qualifying travel, before the per-medium multiplier
     * (Pass 2 / D-AG6). Deliberately per <em>second</em> rather than per block — see
     * {@link com.gmail.nossr50.skills.agility.MovementXpSettings} for why that distinction is the
     * whole balance model.
     */
    public double getMovementBaselineXpPerSecond() {
        return config.getDouble("Experience_Values.Agility.Movement.Baseline_Xp_Per_Second",
                MovementXpSettings.DEFAULT_BASELINE_XP_PER_SECOND);
    }

    /**
     * Blocks per second at which a medium pays its full rate; travel faster than this is clamped and
     * pays no more. Defaults are the plan's starting values — Land is the well-known vanilla sprint
     * speed, Water and Air are estimates pending in-game measurement.
     *
     * @param medium the medium being travelled
     * @return the reference speed in blocks/second
     */
    public double getMovementReferenceSpeed(Medium medium) {
        return config.getDouble(
                "Experience_Values.Agility.Movement.Reference_Speed." + medium.configName(),
                MovementXpSettings.defaultReferenceSpeed(medium));
    }

    /**
     * The per-medium payout weighting applied on top of
     * {@link #getMovementBaselineXpPerSecond()}.
     *
     * @param medium the medium being travelled
     * @return the multiplier, where {@code 1.0} is the baseline rate
     */
    public double getMovementMediumMultiplier(Medium medium) {
        return config.getDouble(
                "Experience_Values.Agility.Movement.Medium_Multiplier." + medium.configName(),
                MovementXpSettings.defaultMediumMultiplier(medium));
    }

    /* Stealth */

    /**
     * Stealth XP paid per second of qualifying sneak-travel (Pass 2). Same speed-normalised model as
     * Agility's movement domains — see {@link com.gmail.nossr50.skills.SpeedNormalisedXp} — because
     * Padfoot raises sneak speed more than threefold and would otherwise multiply its own XP rate.
     */
    public double getSneakBaselineXpPerSecond() {
        return config.getDouble("Experience_Values.Stealth.Sneak.Baseline_Xp_Per_Second",
                StealthXpSettings.DEFAULT_BASELINE_XP_PER_SECOND);
    }

    /**
     * Blocks per second at which sneak-travel pays its full rate; sneaking faster than this (with
     * Padfoot, or Speed potions) is clamped and pays no more.
     */
    public double getSneakReferenceSpeed() {
        return config.getDouble("Experience_Values.Stealth.Sneak.Reference_Speed",
                StealthXpSettings.DEFAULT_SNEAK_REFERENCE_SPEED);
    }

    /**
     * Whether Stealth's anti-AFK gate requires a live movement keypress, not merely a change in
     * position.
     *
     * <p>On by default and it should stay on: sneak-distance is the most farmable XP source in the
     * mod ("sticky keys op", as the wiki puts it). The flag exists because the gate reads
     * {@code ServerPlayerEntity#getPlayerInput()}, and if a client ever stops sending input packets
     * outside vehicles that would silently reduce Stealth's XP to zero rather than merely
     * mis-tuning it. It is an escape hatch for a diagnosable failure, not a balance knob.
     */
    public boolean isSneakInputRequired() {
        return config.getBoolean("ExploitFix.Stealth.Require_Movement_Input", true);
    }

    /* Hunter */

    /**
     * Hunter XP paid for one qualifying kill of a tier-{@code tier} creature (Pass 2).
     *
     * <p>Which tier a creature belongs to is <em>not</em> configured here — it is derived from the
     * creature itself, with a small exception list in {@code advanced.yml → Skills.Hunter.Tiers}. This
     * file owns only what a tier is worth. The split matches the rest of the port: {@code
     * experience.yml} prices things, {@code advanced.yml} decides mechanics.
     *
     * <p>Clamped at zero rather than trusted: a negative value would hand a player negative XP on
     * every kill and walk their level backwards, which is not a tuning choice anybody makes on
     * purpose.
     *
     * @param tier a tier in {@link HunterManager#MIN_TIER}..{@link HunterManager#MAX_TIER}
     */
    public float getHunterXpForTier(int tier) {
        if (tier < HunterManager.MIN_TIER || tier > HunterManager.MAX_TIER) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, config.getDouble("Experience_Values.Hunter.Tier_" + tier,
                HunterManager.DEFAULT_TIER_XP[tier - 1]));
    }

    /* Unarmored */

    /**
     * Unarmored XP paid per point of damage taken while every armour slot is empty (Pass 2).
     *
     * <p>Per damage point, matching how Agility already pays Dodge/Roll/Fall, and measured against
     * the <em>pre-armour</em> damage so Iron Skin does not throttle the very skill that grants it.
     */
    public int getUnarmoredXpPerDamage() {
        return config.getInt("Experience_Values.Unarmored.Damage_Taken",
                UnarmoredManager.DEFAULT_XP_PER_DAMAGE);
    }

    /**
     * Whether Unarmored XP requires a living attacker rather than paying for any damage at all.
     *
     * <p>On by default, and this is the skill's single most important exploit gate: "XP for taking
     * damage" is otherwise farmed by standing in a cactus, a fire or a berry bush with a stack of
     * food, which needs no mobs, no risk and no attention. Requiring an attacker means the XP comes
     * from fighting, which is the thing the skill is meant to reward. The flag exists so the
     * behaviour is diagnosable in play-testing, not as a balance knob.
     *
     * <p>The attacker must also not be the victim themselves — see
     * {@code EntityDamageListener#isUnarmoredXpSource} for why that clause carries its own weight.
     */
    public boolean isUnarmoredLivingAttackerRequired() {
        return config.getBoolean("ExploitFix.Unarmored.Require_Living_Attacker", true);
    }

    /** Default number of Unarmored XP awards a single attacker can pay out before it is spent. */
    public static final int DEFAULT_UNARMORED_MAX_AWARDS_PER_ATTACKER = 20;

    /**
     * How many times one attacker may pay Unarmored XP before it stops counting; {@code 0} or less
     * disables the cap.
     *
     * <p>{@link #isUnarmoredLivingAttackerRequired()} closes the environmental farms (cactus, fire,
     * a berry bush) but not the one that actually matters: a single zombie hitting a player through
     * a slab, with saturation regen healing the damage back as fast as it lands, is a passive
     * ~250 XP/s and reaches RetroMode 1000 in about twelve hours against a design budget of
     * ninety-two. A zombie is a living attacker, so only a per-attacker cap reaches it.
     *
     * <p>The default is deliberately four times Agility Dodge's, which is the same mechanism against
     * the same exploit: the cap must sit far above any fight a player could genuinely have — being
     * hit twenty times by one mob while wearing nothing is already a fight going badly — so that it
     * only ever bites the loop that repeats forever.
     */
    public int getUnarmoredMaxAwardsPerAttacker() {
        return config.getInt("ExploitFix.Unarmored.Max_Awards_Per_Attacker",
                DEFAULT_UNARMORED_MAX_AWARDS_PER_ATTACKER);
    }

    /* Husbandry */

    /**
     * XP for breeding a pair of the given species, keyed by config-entity string (see
     * {@code ConfigStringUtils}) — e.g. {@code Experience_Values.Husbandry.Animal_Breeding.Cow}.
     *
     * <p>Per-species rather than flat because a breeding item's cost spans two orders of magnitude:
     * chicken seeds are free, a horse eats golden carrots, a sniffer needs a torchflower seed dug out
     * of suspicious sand. One flat rate would make the cheapest animal in the game the only one worth
     * breeding.
     *
     * <p><b>An unlisted species resolves to 0 and pays nothing</b>, which is the same contract
     * {@link #getTamingXP} has and is deliberate: the table <em>is</em> the definition of what this
     * skill rewards, so a mob added by a future version or another mod cannot silently start paying a
     * number nobody chose.
     */
    public int getHusbandryBreedXp(String entityConfigString) {
        return config.getInt("Experience_Values.Husbandry.Animal_Breeding." + entityConfigString);
    }

    /**
     * What raising an animal to adulthood pays, as a multiple of what breeding it paid.
     *
     * <p>Shipped at {@code 1.0}. The two halves are equal on purpose: raising is the one part of
     * Husbandry that cannot be rushed — twenty real minutes of vanilla time per animal, shortened
     * only by spending food on the feed verb — so making it worth as much as the clickable half is
     * what stops the skill collapsing into "spam the breeding item".
     */
    public double getHusbandryRaiseMultiplier() {
        return config.getDouble("Experience_Values.Husbandry.Raise_Multiplier",
                HusbandryManager.DEFAULT_RAISE_MULTIPLIER);
    }

    /** XP for feeding a baby animal to accelerate its growth. */
    public int getHusbandryFeedBabyXp() {
        return config.getInt("Experience_Values.Husbandry.Feed_Baby",
                HusbandryManager.DEFAULT_FEED_BABY_XP);
    }

    /** XP for shearing a sheep, mooshroom, snow golem or bogged. */
    public int getHusbandryShearXp() {
        return config.getInt("Experience_Values.Husbandry.Shear", HusbandryManager.DEFAULT_SHEAR_XP);
    }

    /** XP for harvesting honey or honeycomb from a hive or bee nest. */
    public int getHusbandryHiveXp() {
        return config.getInt("Experience_Values.Husbandry.Hive_Harvest",
                HusbandryManager.DEFAULT_HIVE_XP);
    }

    /** XP for milking a cow or bucketing mooshroom stew. */
    public int getHusbandryMilkXp() {
        return config.getInt("Experience_Values.Husbandry.Milk", HusbandryManager.DEFAULT_MILK_XP);
    }

    /** XP for brushing an armadillo's scute off. */
    public int getHusbandryBrushXp() {
        return config.getInt("Experience_Values.Husbandry.Brush", HusbandryManager.DEFAULT_BRUSH_XP);
    }

    /**
     * How long one animal must wait before it can pay a Husbandry harvest award again, in seconds;
     * {@code 0} or less disables the gate.
     *
     * <p>Covers <b>milking and brushing</b>, the two harvest verbs vanilla rate-limits by nothing at
     * all. Right-clicking a cow with a bucket is free and repeatable on the same cow as fast as a
     * player can click; brushing is the same, and the plan had it filed as safe on the strength of a
     * "vanilla scute cooldown" that bytecode says does not exist on that path —
     * {@code brush/armadillo.json} carries no conditions and {@code brushScute} never touches the shed
     * timer. Shearing and hive harvesting are deliberately exempt: grass regrowth and five levels of
     * bee-pollination time already limit them, in-fiction and for free.
     *
     * <p>Same {@code MetadataStore} mechanism as Unarmored's per-attacker cap and Agility's Dodge cap.
     * The counter is transient, which is right for a five-minute window — it lives as long as the
     * animal does and is not saved.
     */
    public int getHusbandryHarvestCooldownSeconds() {
        return config.getInt("ExploitFix.Husbandry.Harvest_Cooldown_Seconds",
                HusbandryManager.DEFAULT_HARVEST_COOLDOWN_SECONDS);
    }

    /**
     * How many breedings may pay Husbandry XP inside one award window; {@code 0} disables the cap.
     *
     * <p><b>The skill's headline anti-exploit gate</b> since GitHub #3 (2026-08-04), and it replaced
     * one that did not work. The old gate capped how many animals a single breeding item could set in
     * love, which bounded XP <em>per item</em> — and wheat is free, so twenty clicks in one breath
     * still paid fifty breedings. Counting payouts per unit of <em>time</em> is what closes that,
     * because time is the one input a farm cannot manufacture. See
     * {@link HusbandryManager#DEFAULT_BREED_XP_AWARDS_PER_WINDOW}.
     */
    public int getHusbandryBreedXpAwardsPerWindow() {
        return config.getInt("ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window",
                HusbandryManager.DEFAULT_BREED_XP_AWARDS_PER_WINDOW);
    }

    /**
     * How long one breed-XP award window lasts, in seconds; {@code 0} disables the cap.
     *
     * <p>The shipped thirty seconds is <b>derived rather than tuned</b>: it is vanilla's own love
     * duration ({@code AnimalEntity#lovePlayer} sets {@code loveTicks = 600}), so every breeding one
     * breeding item can cause falls inside a single window. Shortening it silently raises the
     * effective cap by splitting one click's burst in two — see
     * {@link HusbandryManager#DEFAULT_BREED_XP_AWARD_WINDOW_SECONDS}.
     */
    public int getHusbandryBreedXpAwardWindowSeconds() {
        return config.getInt("ExploitFix.Husbandry.Breed_Xp_Award_Window_Seconds",
                HusbandryManager.DEFAULT_BREED_XP_AWARD_WINDOW_SECONDS);
    }

    /* Archery */
    public double getArcheryDistanceMultiplier() {
        return config.getDouble("Experience_Values.Archery.Distance_Multiplier", 0.025);
    }

    /* Fishing */
    public int getFishingShakeXP() {
        return config.getInt("Experience_Values.Fishing.Shake", 50);
    }

    /* Repair */
    public double getRepairXPBase() {
        return config.getDouble("Experience_Values.Repair.Base", 1000.0);
    }

    /**
     * The per-{@link MaterialType} Repair XP factor (legacy {@code getRepairXP}). Keyed by the
     * capitalized material-family name, e.g. {@code Experience_Values.Repair.Iron}. Multiplied by
     * {@link #getRepairXPBase()} and the repairable's own multiplier when a repair is performed.
     */
    public double getRepairXP(MaterialType repairMaterialType) {
        return config.getDouble("Experience_Values.Repair." + StringUtils.getCapitalized(
                repairMaterialType.toString()));
    }

    /* Smelting — keyed by the smelted input material's config string (see ConfigStringUtils), e.g.
     * {@code "Iron_Ore"}. Legacy {@code Smelting.getResourceXp} looked up the input item; ores /
     * raw metals carry a value, everything else resolves to 0 (no reward). */
    public int getSmeltingXP(String materialConfigString) {
        return config.getInt("Experience_Values.Smelting." + materialConfigString);
    }

    /* Taming — keyed by config-entity string (see ConfigStringUtils). */
    public int getTamingXP(String entityConfigString) {
        return config.getInt("Experience_Values.Taming.Animal_Taming." + entityConfigString);
    }

    /* Alchemy — XP for brewing a potion of the given stage (1..5). Legacy default 10. */
    public double getPotionXP(PotionStage stage) {
        return config.getDouble(
                "Experience_Values.Alchemy.Potion_Brewing.Stage_" + stage.toNumerical(), 10D);
    }

    /**
     * Validates the XP tuning values. Returns {@code true} when no problems were found; problems are
     * logged. Alchemy (PotionStage) and repair-material (MaterialType) checks are omitted until
     * those skills port.
     */
    protected boolean validateKeys() {
        final List<String> reason = new ArrayList<>();

        /* Curve values */
        if (getMultiplier(FormulaType.EXPONENTIAL) <= 0) {
            reason.add("Experience_Formula.Exponential_Values.multiplier should be greater than 0!");
        }
        if (getMultiplier(FormulaType.LINEAR) <= 0) {
            reason.add("Experience_Formula.Linear_Values.multiplier should be greater than 0!");
        }
        if (getExponent(FormulaType.EXPONENTIAL) <= 0) {
            reason.add("Experience_Formula.Exponential_Values.exponent should be greater than 0!");
        }

        /* Global modifier */
        if (getExperienceGainsGlobalMultiplier() <= 0) {
            reason.add("Experience_Formula.Multiplier.Global should be greater than 0!");
        }

        /* PVP modifier */
        if (getPlayerVersusPlayerXP() < 0) {
            reason.add("Experience_Formula.Multiplier.PVP should be at least 0!");
        }

        /* Spawned Mob modifier */
        if (getSpawnedMobXpMultiplier() < 0) {
            reason.add("Experience_Formula.Mobspawners.Multiplier should be at least 0!");
        }

        /* Bred Mob modifier */
        if (getBredMobXpMultiplier() < 0) {
            reason.add("Experience_Formula.Breeding.Multiplier should be at least 0!");
        }

        /* Conversion */
        if (getExpModifier() <= 0) {
            reason.add("Conversion.Exp_Modifier should be greater than 0!");
        }

        /* Archery */
        if (getArcheryDistanceMultiplier() < 0) {
            reason.add("Experience_Values.Archery.Distance_Multiplier should be at least 0!");
        }

        /* Combat XP Multipliers */
        if (getAnimalsXP() < 0) {
            reason.add("Experience_Values.Combat.Multiplier.Animals should be at least 0!");
        }

        /* Agility */
        if (getDodgeXPModifier() < 0) {
            reason.add("Experience_Values.Agility.Dodge should be at least 0!");
        }
        if (getRollXPModifier() < 0) {
            reason.add("Experience_Values.Agility.Roll should be at least 0!");
        }
        if (getFallXPModifier() < 0) {
            reason.add("Experience_Values.Agility.Fall should be at least 0!");
        }

        /* Fishing */
        if (getFishingShakeXP() <= 0) {
            reason.add("Experience_Values.Fishing.Shake should be greater than 0!");
        }

        /* Repair */
        if (getRepairXPBase() <= 0) {
            reason.add("Experience_Values.Repair.Base should be greater than 0!");
        }

        /* Taming */
        if (getTamingXP("Wolf") <= 0) {
            reason.add("Experience_Values.Taming.Animal_Taming.Wolf should be greater than 0!");
        }
        if (getTamingXP("Ocelot") <= 0) {
            reason.add("Experience_Values.Taming.Animal_Taming.Ocelot should be greater than 0!");
        }

        for (String issue : reason) {
            LOGGER.warn(issue);
        }
        return reason.isEmpty();
    }
}
