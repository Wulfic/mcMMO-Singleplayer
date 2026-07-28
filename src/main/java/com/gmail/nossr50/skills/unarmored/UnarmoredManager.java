package com.gmail.nossr50.skills.unarmored;

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
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Unarmored — the skill for people who fight in their shirt. XP comes from being hit while every
 * armour slot is empty; the payoff is innate "skin" armour that grows through four tiers
 * (Iron Skin) and a small retaliatory sting (Thorny Skin).
 *
 * <p><b>MC-free by construction</b>, like every other manager. The platform layer decides
 * <em>whether</em> the player is unarmored and who hit them; this class owns the arithmetic and the
 * gates, so all of it is unit-testable.
 *
 * <h2>The one rule the whole skill hangs off</h2>
 * Every payoff here takes an {@code unarmored} boolean and returns zero when it is {@code false}.
 * That is the skill's entire premise — it rewards going without armour — and expressing it as a
 * parameter rather than as a caller-side {@code if} means a caller cannot forget the check and
 * quietly hand a fully-plated player free armour points.
 *
 * <h2>Design rulings (see {@code plans/new-skills/unarmored.md})</h2>
 * <ul>
 *   <li><b>D-U1 — the "doubles real armour" clause is CUT.</b> The source wiki says the skin applies
 *       "when not wearing armor" and then that real armour is "doubled"; those contradict, and only
 *       the first is the skill. A wearing-armour synergy would be a different skill.</li>
 *   <li><b>D-U2 — stepped tiers, not continuous scaling.</b> The four ranks of
 *       {@link SubSkillType#UNARMORED_IRON_SKIN} <em>are</em> the four tiers, so the rank number
 *       indexes the armour table directly and there is no second set of breakpoint levels in
 *       {@code advanced.yml} to drift out of step with {@code skillranks.yml}.</li>
 *   <li><b>Armour points only, no toughness.</b> Toughness is what blunts <em>large</em> hits, so
 *       withholding it is what keeps a real armour set worth wearing: diamond skin (20 armour, 0
 *       toughness) still takes noticeably more from a heavy blow than a diamond set (20 armour, 8
 *       toughness), and real armour keeps its enchantments on top.</li>
 * </ul>
 */
public class UnarmoredManager extends SkillManager {

    /**
     * The most damage a single hit can be paid XP for.
     *
     * <p>One full vanilla health bar. Without it a one-shot from a charged creeper or a
     * damage-boosted mob pays a jackpot proportional to a number the player never survives, and any
     * mod or command that deals absurd damage becomes an XP button. Legacy applies the same clamp,
     * at the same value, to Roll's fall XP ({@code AgilityManager#calculateRollXP}).
     */
    public static final double MAX_CREDITED_DAMAGE = 20.0;

    /** Fallback XP per point of damage taken, when no {@code experience.yml} is loaded. */
    public static final int DEFAULT_XP_PER_DAMAGE = 100;

    /**
     * Armour points per Iron Skin rank, indexed 1-4 — the wiki's leather / gold / iron / diamond
     * tiers, matching the real armour sets. Index 0 is the no-rank case and is deliberately present
     * so {@link #getSkinArmorPoints} can index without an off-by-one branch.
     */
    private static final double[] DEFAULT_TIER_ARMOR_POINTS = {0.0, 7.0, 11.0, 15.0, 20.0};

    /** Fallback maximum Thorny Skin reflect, in damage points. Half a heart. */
    public static final double DEFAULT_MAX_THORNS_DAMAGE = 1.0;

    public UnarmoredManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.UNARMORED);
    }

    // --- XP ---------------------------------------------------------------------------------

    /**
     * XP for a single hit taken while unarmored.
     *
     * <p><b>The damage passed in is deliberately the PRE-armour figure.</b> Iron Skin is itself an
     * armour bonus, so paying on what actually landed would make the skill slow its own progress
     * down: at the diamond tier roughly two thirds of every hit is absorbed, so the last and longest
     * stretch of the grind would run at a third rate — which reads to a player as a bug rather than
     * as a design. The caller sources this from the pre-armour seam for exactly that reason.
     *
     * @param preArmorDamage the incoming damage before any armour mitigation
     * @return the XP to award; {@code 0} for a non-positive hit
     */
    public float getUnarmoredXp(double preArmorDamage) {
        if (preArmorDamage <= 0) {
            return 0F;
        }
        return (float) (Math.min(MAX_CREDITED_DAMAGE, preArmorDamage) * getXpPerDamage());
    }

    /** XP paid per point of damage taken ({@code experience.yml}); never negative. */
    public int getXpPerDamage() {
        final ExperienceConfig experience = McMMOMod.getExperienceConfig();
        if (experience == null) {
            return DEFAULT_XP_PER_DAMAGE;
        }
        return Math.max(0, experience.getUnarmoredXpPerDamage());
    }

    /**
     * Credit a hit taken while unarmored.
     *
     * @param preArmorDamage the incoming damage before any armour mitigation (see
     *                       {@link #getUnarmoredXp})
     * @return the XP awarded, or {@code 0} when the hit was worth nothing
     */
    public float onDamageTaken(double preArmorDamage) {
        final float xp = getUnarmoredXp(preArmorDamage);
        if (xp <= 0) {
            return 0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }

    // --- Sub-skill 1: Iron Skin -----------------------------------------------------------------

    public boolean canIronSkin() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.UNARMORED_IRON_SKIN)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.UNARMORED_IRON_SKIN);
    }

    /**
     * Which armour tier this player has reached: {@code 0} for none, then 1-4 for leather, gold,
     * iron and diamond.
     *
     * <p>Read straight off the sub-skill's rank rather than compared against breakpoint levels of
     * its own, so the tiers are configured in exactly one file ({@code skillranks.yml}) and moving a
     * breakpoint cannot leave the two definitions disagreeing.
     */
    public int getIronSkinTier() {
        if (!canIronSkin()) {
            return 0;
        }
        final int rank = RankUtils.getRank(mmoPlayer, SubSkillType.UNARMORED_IRON_SKIN);
        // Clamped rather than trusted: getRank is driven by skillranks.yml, and a file that declares
        // more ranks than this skill has tiers would otherwise index off the end of the table.
        return Math.max(0, Math.min(DEFAULT_TIER_ARMOR_POINTS.length - 1, rank));
    }

    /**
     * The armour points the skin is worth right now.
     *
     * <p>Returns {@code 0} for an armoured player, always — the whole skill is the trade of armour
     * for skin, and the caller states which of the two the player has chosen this tick.
     *
     * @param unarmored whether every armour slot is currently empty
     * @return armour points to apply, {@code 0} when armoured or when Iron Skin is locked
     */
    public double getSkinArmorPoints(boolean unarmored) {
        if (!unarmored) {
            return 0.0;
        }
        final int tier = getIronSkinTier();
        if (tier <= 0) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        final double points = advanced == null
                ? DEFAULT_TIER_ARMOR_POINTS[tier]
                : advanced.getIronSkinArmorPoints(tier, DEFAULT_TIER_ARMOR_POINTS[tier]);
        // Never negative: a mistyped config must not hand out a penalty on a skill whose entire
        // purpose is a bonus.
        return Math.max(0.0, points);
    }

    // --- Sub-skill 2: Thorny Skin ---------------------------------------------------------------

    public boolean canThornySkin() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.UNARMORED_THORNY_SKIN)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.UNARMORED_THORNY_SKIN);
    }

    /**
     * Whether a melee attacker should be stung right now.
     *
     * @param unarmored whether every armour slot is currently empty
     */
    public boolean thornsReady(boolean unarmored) {
        return unarmored && canThornySkin() && getThornsDamage(unarmored) > 0;
    }

    /**
     * How much damage to reflect back at a melee attacker.
     *
     * <p>Kept deliberately tiny — the wiki's own cap is half a heart — because a reflect that scales
     * with anything becomes a mob-melter: it costs the player nothing, needs no aiming and fires on
     * every hit taken, so any value large enough to feel good is large enough to kill things by
     * standing still.
     *
     * @param unarmored whether every armour slot is currently empty
     * @return the reflect damage, or {@code 0} when armoured or when Thorny Skin is locked
     */
    public double getThornsDamage(boolean unarmored) {
        if (!unarmored || !canThornySkin()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return scaleToLevel(DEFAULT_MAX_THORNS_DAMAGE, 0);
        }
        return scaleToLevel(Math.max(0.0, advanced.getThornySkinMaxReflectDamage()),
                advanced.getThornySkinMaxBonusLevel());
    }
}
