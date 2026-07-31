package com.gmail.nossr50.skills.hunter;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Hunter — the mob-knowledge skill. The more of one creature you have personally killed, the better
 * you get at killing <em>that</em> creature.
 *
 * <p><b>MC-free by construction</b>, like every other manager: this class owns the arithmetic and the
 * gates, the platform layer decides what died and who killed it.
 *
 * <h2>Two axes, deliberately independent</h2>
 * <table>
 *   <caption>Hunter's two progression axes</caption>
 *   <tr><th>Axis</th><th>Currency</th><th>Reward</th></tr>
 *   <tr><td><b>Mob mastery</b> (horizontal)</td><td>kills of <em>one</em> mob type</td>
 *       <td>flat bonus damage against that mob only, at three fixed thresholds, never reset</td></tr>
 *   <tr><td><b>Hunter level</b> (vertical)</td><td>XP from any qualifying kill</td>
 *       <td>increased loot, unlocked per mob tier, on the normal {@code 10N² + 1010N} curve</td></tr>
 * </table>
 *
 * <p>Killing 10,000 zombies makes you a zombie specialist; killing 200 of everything makes you a
 * generalist with better drops. Neither substitutes for the other, and that separation is the reason
 * the skill is worth building — it must survive any balance pass intact.
 *
 * <h2>⚠️ Mob Mastery does not fit {@code RankUtils}, and must not be forced through it</h2>
 * Every other sub-skill in the mod unlocks on <em>skill level</em> via {@code skillranks.yml}.
 * Mastery unlocks on a <em>per-mob counter</em>, which no rank config can express — so the tier
 * resolver lives here, on the manager. Routing it through {@code RankUtils} would produce a sub-skill
 * whose rank display lies.
 *
 * <h2>Scope as of stage 5 — read this before adding to the class</h2>
 * Both axes: the kill counters and the threshold arithmetic they feed (with the bonus damage a tier
 * is worth), and the mob-tier rule with the XP each tier pays.
 *
 * <p>{@link #masteryDamageBonusForHit} is spent by {@code EntityDamageListener#applyHunterMastery},
 * which runs <b>last</b> in that chain — after Sprint Smash and after Stealth Assassin — because
 * Assassin multiplies the whole melee total, so a Hunter bonus applied first would be multiplied
 * along with it. {@link #awardKillXp} is spent by {@code HunterListener}, behind the same four gates
 * the counter passes. Trophy Hunter's loot re-roll and Quarry Sense are stages 6–7 and are
 * deliberately absent rather than stubbed.
 *
 * @see <a href="file:../../../../../../../plans/new-skills/hunter.md">plans/new-skills/hunter.md</a>
 */
public class HunterManager extends SkillManager {

    /**
     * Kills of one mob type needed for each mastery tier, ascending.
     *
     * <p>Fixed rather than configurable-per-tier for now: three numbers a player can learn are worth
     * more than three knobs nobody turns, and the whole point of the horizontal axis is that it means
     * the same thing for every mob. Parallel to {@link #MASTERY_DAMAGE_BONUS} — index {@code i} of one
     * belongs with index {@code i} of the other.
     *
     * <p>The gap between hand-killing and farming these numbers <em>is</em> the feature: at a
     * sustained ~6 kills/min, 10,000 kills is roughly 28 hours; a gold farm produces 3,000+/hour and
     * would do it in an afternoon. Stage 1's spawn-origin gate is what separates the two, and it is a
     * prerequisite of this counter rather than a follow-up to it.
     */
    public static final int[] MASTERY_THRESHOLDS = {500, 2_500, 10_000};

    /**
     * Bonus damage at each {@link #MASTERY_THRESHOLDS} tier: half a heart, one heart, one and a half.
     *
     * <p><b>Halved from the drafted +2/+4/+6 by user ruling on 2026-07-30</b>, and the reason is worth
     * keeping: this is a <em>flat</em> add, so it is proportionally worst for the strongest weapon and
     * absurd for the weakest. At +6.0 a bare fist hits for 7.0 — a diamond sword's worth of punch from
     * kill counts alone, on top of whatever Unarmed already adds, compounding with Stealth Assassin. At
     * +3.0 the multipliers are netherite 1.375×, wooden 1.75×, bare fist 4×.
     */
    public static final double[] MASTERY_DAMAGE_BONUS = {1.0, 2.0, 3.0};

    /**
     * Shipped {@code Skills.Hunter.MobMastery.Ranged_Damage_Multiplier}: the mastery bonus is worth
     * exactly as much from a bow as from a blade.
     *
     * <p>Ships at {@code 1.0} on purpose — the ruling is that mastery applies to <em>any</em> damage
     * the player is responsible for, and a knob whose default changes the ruled behaviour would be a
     * config that lies. It exists because the ranged half is the part of D-HU4 most likely to need a
     * tuning pass in §G (a capped +3.0 on every fully-drawn arrow), and having it here means that pass
     * is a config edit rather than a code change.
     */
    public static final double DEFAULT_RANGED_DAMAGE_MULTIPLIER = 1.0;

    // --- Hunter level: the mob tiers -------------------------------------------------------------

    /** The lowest tier a mob can resolve to. */
    public static final int MIN_TIER = 1;

    /** The highest tier a mob can resolve to. */
    public static final int MAX_TIER = 4;

    /**
     * Shipped XP per qualifying kill, indexed by tier − 1.
     *
     * <p>Derived, not picked. The curve is {@code 10N² + 1010N}, so a RetroMode level-1000 Hunter
     * costs <b>11,010,000</b> XP. At a sustained hand-killing rate of ~6 kills/min (360/h) the
     * ~100 h target wants ≈306 XP for an average kill, which is what puts common hostiles — the
     * overwhelming majority of what anyone actually kills — at 300.
     *
     * <p>T4 is <b>1,500 and not the drafted 5,000</b> by user ruling on 2026-07-30: 5,000 would let a
     * wither farm outrun the 80 h guardrail inherited from Agility's D-AG6, and a tier nobody can
     * safely reach is worse than a cheap one.
     */
    public static final int[] DEFAULT_TIER_XP = {100, 300, 800, 1_500};

    /**
     * Max health at or above which a hostile is a boss (T4). Warden 500, wither 300, ender dragon 200
     * — and the next-largest thing in the game is a 100 HP ravager, so the gap either side of this
     * number is enormous. That margin is the point: it is what stops a modded "elite zombie" landing
     * in the boss tier by accident.
     */
    static final double BOSS_HEALTH = 150.0;

    /** Max health at or above which a hostile is dangerous (T3): guardian, shulker, enderman, ravager. */
    static final double DANGEROUS_HEALTH = 30.0;

    /**
     * Attack damage at or above which a hostile is dangerous (T3) whatever its health — this is what
     * catches the blaze (20 HP, 6.0 damage).
     *
     * <p>⚠️ <b>6.0 and not 5.0, deliberately.</b> At 5.0 the rule also promotes the zombified piglin
     * and the piglin, and a gold farm is the single most-built grinder in the game; the origin gate
     * does not close it, because nether-wastes piglins are legitimately {@code NATURAL}. Paying 800
     * instead of 300 there would make the worst farm in the port two and a half times worse for one
     * point of nominal attack damage.
     */
    static final double DANGEROUS_ATTACK_DAMAGE = 6.0;

    /**
     * Max health at or above which a <em>non</em>-hostile creature is promoted out of T1 — the iron
     * golem (100 HP) is the only vanilla mob it catches.
     *
     * <p>It exists for the mobs that do not exist yet. A modded 300 HP passive guardian-beast should
     * not pay a chicken's XP, and the promotion is capped at T2 rather than scaling on to T3/T4
     * because health alone cannot tell "tanky" from "dangerous". Horses top out at 53 HP and stay in
     * T1, which is the intended side of the line.
     */
    static final double HEAVYWEIGHT_PASSIVE_HEALTH = 60.0;

    public HunterManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.HUNTER);
    }

    // --- Mob mastery: the per-mob counters ------------------------------------------------------

    /**
     * How many of {@code mobId} this player has killed.
     *
     * @param mobId the mob's raw registry id, e.g. {@code minecraft:zombie}
     */
    public int getKills(@NotNull String mobId) {
        return mmoPlayer.getProfile().getMobKills(mobId);
    }

    /**
     * Count one kill and hand back the new total.
     *
     * <p>The caller is responsible for the gate — a farmed mob must never reach here. Attribution,
     * transient summons, player-built golems and spawn origin are all decided in the platform layer
     * where the entity is; this method's contract is simply "this kill counts".
     *
     * @param mobId the mob's raw registry id
     * @return the count after this kill
     */
    public int recordKill(@NotNull String mobId) {
        return mmoPlayer.getProfile().incrementMobKills(mobId);
    }

    /** Every mob type this player has killed, and how often. Unmodifiable. */
    public @NotNull Map<String, Integer> getAllKills() {
        return mmoPlayer.getProfile().getAllMobKills();
    }

    // --- Mob mastery: the threshold arithmetic --------------------------------------------------

    /**
     * The mastery tier a kill count has reached: {@code 0} for none, then 1–3.
     *
     * <p>Walks the whole table upward rather than returning on the first match, so the tiers cannot be
     * got wrong by reordering them and a count past the last threshold clamps at the top tier instead
     * of running off the end.
     *
     * @param killsOfThisMob kills of the mob in question; a negative count reads as none
     */
    public int masteryTier(int killsOfThisMob) {
        int tier = 0;
        for (int i = 0; i < MASTERY_THRESHOLDS.length; i++) {
            if (killsOfThisMob >= MASTERY_THRESHOLDS[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /** The mastery tier this player has reached against {@code mobId}. */
    public int masteryTierAgainst(@NotNull String mobId) {
        return masteryTier(getKills(mobId));
    }

    /**
     * The flat bonus damage a kill count is worth, capped at the top tier.
     *
     * <p>Indexed off {@link #masteryTier} rather than compared against the thresholds a second time,
     * so the two can never disagree about where a tier begins.
     *
     * @param killsOfThisMob kills of the mob being hit
     * @return {@code 0.0} below the first threshold, else that tier's bonus
     */
    public double masteryDamageBonus(int killsOfThisMob) {
        final int tier = masteryTier(killsOfThisMob);
        return tier <= 0 ? 0.0 : MASTERY_DAMAGE_BONUS[tier - 1];
    }

    /** The flat bonus damage this player currently gets against {@code mobId}. */
    public double masteryDamageBonusAgainst(@NotNull String mobId) {
        return masteryDamageBonus(getKills(mobId));
    }

    /**
     * What this player's mastery is worth on <em>one hit</em> against {@code mobId} — the figure the
     * damage seam actually adds.
     *
     * <h2>The melee/ranged asymmetry is deliberate, and it is the port's existing pattern</h2>
     * A melee bonus is scaled by the captured attack-cooldown charge
     * ({@link McMMOPlayer#getAttackStrength()}), exactly as every other melee bonus in this port is
     * ({@code MeleeDamageBonus} scales Stab, Axe Mastery, Crush, Steel Arm and the melee half of
     * Impale the same way). Without it, spam-clicking would out-damage a charged swing — an exploit,
     * and off-pattern for the whole codebase.
     *
     * <p>A ranged bonus is <b>not</b> scaled, because a throw or a loosed arrow has no swing to
     * charge; the same asymmetry Trident Impale already ships. That is not merely stylistic here:
     * {@code attackStrength} is a field stamped at melee-swing time, so on a projectile hit it holds
     * whatever the player's last <em>swing</em> left behind — reading it would make an archer's bonus
     * depend on how recently they punched something.
     *
     * @param mobId the victim's raw registry id, e.g. {@code minecraft:zombie}
     * @param melee whether the hit was a melee swing rather than a player-fired projectile
     * @return the damage to add to this hit; {@code 0.0} below the first mastery threshold
     */
    public double masteryDamageBonusForHit(@NotNull String mobId, boolean melee) {
        final double bonus = masteryDamageBonusAgainst(mobId);
        if (bonus <= 0.0) {
            return 0.0;
        }
        return melee ? bonus * mmoPlayer.getAttackStrength() : bonus * rangedMultiplier();
    }

    /**
     * The {@code Ranged_Damage_Multiplier} tuning knob, or its shipped default.
     *
     * <p>⚠️ <b>Falls back to {@link #DEFAULT_RANGED_DAMAGE_MULTIPLIER}, not to zero</b>, and the
     * direction matters: this value is a <em>multiplier</em>, so a defensive {@code 0.0} would not
     * fail safe, it would silently delete the whole ranged half of the sub-skill the moment the config
     * service were unavailable. Contrast {@code StealthManager#getPadfootSpeedBonus}, where the config
     * value <em>is</em> the bonus and zero is correctly "no effect".
     */
    private double rangedMultiplier() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null
                ? DEFAULT_RANGED_DAMAGE_MULTIPLIER
                : advanced.getHunterMasteryRangedDamageMultiplier();
    }

    /**
     * Whether this kill just crossed a mastery threshold — the trigger for stage 3's notification.
     *
     * <p>Expressed as "did the tier change" rather than "is the count exactly a threshold" on purpose:
     * a single kill is the only thing that moves the counter today, but a future bulk grant (a command,
     * a data fix) that skipped a threshold would otherwise swallow the notification silently.
     *
     * @param killsBefore the count before the kill
     * @param killsAfter  the count after it
     */
    public boolean crossedMasteryThreshold(int killsBefore, int killsAfter) {
        return masteryTier(killsAfter) > masteryTier(killsBefore);
    }

    // --- Hunter level: tiers and the XP they pay -------------------------------------------------

    /**
     * The tier a creature belongs to, derived from what it <em>is</em> rather than from a table
     * somebody has to maintain.
     *
     * <h2>⚠️ Why this is derived at all — it is the whole point of D-HU5</h2>
     * The obvious implementation is a ~90-row per-mob table, and the port has been bitten by exactly
     * that shape three times: {@code Nautilus} and {@code Happy_Ghast} were missing from Husbandry's
     * breed table, so two verbs paid <b>zero</b> for both species, and three mobs shook nothing out in
     * Fishing for the same reason. A hand-authored table's failure mode is a silent {@code 0}, and it
     * goes stale the moment Mojang adds a mob or the player installs one. A derived default cannot.
     *
     * <h2>⚠️ {@code experience.yml}'s ready-made mob table is NOT usable as the source</h2>
     * {@code Experience_Values.Combat.Multiplier} is a ~90-row per-mob table that already exists, and
     * it is the wrong one: it prices XP-per-point-of-damage, not danger. The witch is {@code 0.1}, the
     * warden {@code 6.0} and the <b>ender dragon {@code 1.0}</b> — deriving tiers from it would put the
     * dragon in T1 alongside the chicken.
     *
     * <h2>The rule, and the vanilla numbers it was calibrated against</h2>
     * <ol>
     *   <li>Not hostile → <b>T1</b>, unless it is a heavyweight ({@link #HEAVYWEIGHT_PASSIVE_HEALTH}),
     *       which caps at T2. Killing a cow is not a hunt however much health the cow has.</li>
     *   <li>Hostile and {@link #BOSS_HEALTH} or more → <b>T4</b>.</li>
     *   <li>Hostile and either {@link #DANGEROUS_HEALTH} health or {@link #DANGEROUS_ATTACK_DAMAGE}
     *       damage → <b>T3</b>.</li>
     *   <li>Any other hostile → <b>T2</b>.</li>
     * </ol>
     *
     * <p><b>It fails low, on purpose.</b> Every uncertain case resolves downward — a non-hostile
     * heavyweight stops at T2, an unrecognised mob with no attribute container at all reads as 0
     * health and 0 damage and lands in T1/T2. Hunter XP is the axis a mob farm attacks, so the safe
     * direction for a mistake is "pays too little", never "pays too much".
     *
     * <p>Two vanilla creatures the rule gets wrong are corrected by the override table rather than by
     * bending the rule around them, and both fail the same way: <b>their danger is not in their
     * attributes</b>. A ghast has 10 HP and no {@code ATTACK_DAMAGE} entry whatsoever, because it
     * throws fireballs; a wither skeleton's {@code ATTACK_DAMAGE} is the inherited default 2.0,
     * identical to a plain skeleton's, because its sword and its wither effect do the work. Contrast
     * the witch, which the plan flagged as a likely override and which the rule places correctly at T2
     * for free (26 HP is below the T3 line).
     *
     * @param hostile      whether this creature is a monster rather than a passive or neutral one
     * @param maxHealth    its base max health, equipment excluded
     * @param attackDamage its base melee attack damage, or {@code 0} when it has no such attribute
     * @return a tier in {@link #MIN_TIER}..{@link #MAX_TIER}
     */
    public static int deriveTier(boolean hostile, double maxHealth, double attackDamage) {
        if (!hostile) {
            return maxHealth >= HEAVYWEIGHT_PASSIVE_HEALTH ? 2 : MIN_TIER;
        }
        if (maxHealth >= BOSS_HEALTH) {
            return MAX_TIER;
        }
        if (maxHealth >= DANGEROUS_HEALTH || attackDamage >= DANGEROUS_ATTACK_DAMAGE) {
            return 3;
        }
        return 2;
    }

    /**
     * The tier a creature belongs to: the operator's override if there is one, else
     * {@link #deriveTier}.
     *
     * <p>The override table is the exception list, not the table — see {@code AdvancedConfig
     * #getHunterTierOverride}. An out-of-range or unreadable entry is discarded there and this falls
     * through to the derived value, so a typo in {@code advanced.yml} costs the operator their
     * override and not the mob's XP.
     *
     * @param configKey    the mob's config key, e.g. {@code Wither_Skeleton}
     * @param hostile      whether this creature is a monster
     * @param maxHealth    its base max health
     * @param attackDamage its base melee attack damage, or {@code 0} when it has none
     */
    public static int resolveTier(@NotNull String configKey, boolean hostile, double maxHealth,
            double attackDamage) {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced != null) {
            final int override = advanced.getHunterTierOverride(configKey);
            if (override >= MIN_TIER && override <= MAX_TIER) {
                return override;
            }
        }
        return deriveTier(hostile, maxHealth, attackDamage);
    }

    /**
     * The XP one qualifying kill of a tier-{@code tier} creature pays.
     *
     * <p>⚠️ <b>Falls back to the shipped {@link #DEFAULT_TIER_XP} when the config is unavailable, not
     * to zero.</b> Same reasoning as {@code Ranged_Damage_Multiplier}'s {@code 1.0} fallback: a
     * defensive zero here would not fail safe, it would silently stop the whole vertical axis of the
     * skill — a player would kill for an hour and gain nothing, with no error to point at.
     *
     * @param tier a tier in {@link #MIN_TIER}..{@link #MAX_TIER}; anything outside pays nothing
     */
    public static float xpForTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            return 0.0F;
        }
        final ExperienceConfig experience = McMMOMod.getExperienceConfig();
        return experience == null
                ? DEFAULT_TIER_XP[tier - 1]
                : experience.getHunterXpForTier(tier);
    }

    /**
     * Pay this player for one qualifying kill of a tier-{@code tier} creature.
     *
     * <p>Routed through {@link #applyXpGain} rather than {@code beginXpGain} directly so Hunter
     * inherits the whole shared pipeline — the skill multiplier, the global modifier, diminishing
     * returns and the XP bar — exactly as every other skill does.
     *
     * <h2>Per kill, not per hit — and Hunter is the only combat skill like that</h2>
     * Swords, Axes, Archery and the rest pay {@code damage × Combat.Multiplier} on <em>every hit</em>
     * ({@code CombatXp}, the 2026-07-17 ruling). Hunter cannot: its subject is <em>which creature</em>
     * died, which is not a question a hit can answer, and paying per hit would make a 500 HP warden
     * worth twenty-five times a 20 HP zombie for reasons that have nothing to do with the tier the
     * player is being paid for. The tier already prices the danger.
     *
     * @param tier the victim's tier
     * @return the XP awarded, for the caller to log or assert on; {@code 0} for an invalid tier
     */
    public float awardKillXp(int tier) {
        final float xp = xpForTier(tier);
        if (xp <= 0.0F) {
            return 0.0F;
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return xp;
    }
}
