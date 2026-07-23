package com.gmail.nossr50.skills.acrobatics;

import com.gmail.nossr50.datatypes.BlockLocationHistory;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.DodgeResult;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.Probability;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Acrobatics skill manager. Both the <b>Roll / Graceful Roll</b> fall-damage path (K2) and the
 * <b>Dodge</b> combat path (K1) are live, driven by the {@code modifyAppliedDamage} damage mixin via
 * {@link com.gmail.nossr50.fabric.listeners.EntityDamageListener}. The Dodge XP anti-farm cap
 * (per-mob dodge tracker) and the lightning-dodge exclusion live in the listener (the MC-typed layer,
 * which has the attacker entity); this manager takes the attacker's XP-eligibility as a boolean so its
 * damage-reduction + XP math stays deterministic and unit-testable.
 *
 * <p>Port note: the legacy Roll logic lived in the {@code AbstractSubSkill}-based
 * {@code datatypes.skills.subskills.acrobatics.Roll}, tightly coupled to Bukkit's
 * {@code EntityDamageEvent}. It is folded into this manager MC-free: the RNG orchestration
 * ({@link #processFallDamage}) is verified in-game, while the deterministic pieces
 * ({@link #rollCheck}, {@link #calculateRollXP}, the exploit gate, and the fall-location throttle)
 * are unit-testable. Sound/notification feedback is emitted by the listener (the MC-typed layer), not
 * here, so this class imports no Minecraft types.
 */
public class AcrobaticsManager extends SkillManager {

    public AcrobaticsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.ACROBATICS);
        this.fallLocationMap = new BlockLocationHistory(50);
    }

    private long rollXPCooldown = 0;
    private final long rollXPInterval = (1000 * 3);
    private long rollXPIntervalLengthen = (1000 * 10);
    private final BlockLocationHistory fallLocationMap;

    /**
     * Processes an incoming fall-damage hit: rolls for Roll/Graceful Roll, awards Acrobatics XP, and
     * records the landing block to throttle repeat-farming. Called from the damage listener with the
     * (post-armor/protection) fall damage.
     *
     * @param baseDamage the incoming fall damage
     * @return the {@link RollResult} when the Roll subskill is unlocked and the fall was survivable
     *         (the caller applies {@link RollResult#getModifiedDamage()} + feedback on a success);
     *         {@code null} when the fall was fatal, or when Roll is not unlocked (fall XP is still
     *         awarded internally in that case, but the damage is left unchanged)
     */
    public @Nullable RollResult processFallDamage(double baseDamage) {
        if (canRoll()) {
            final boolean isGraceful = getPlayer().isSneaking();
            final Probability probability = isGraceful
                    ? ProbabilityUtil.getGracefulRollProbability(mmoPlayer)
                    : ProbabilityUtil.getSubSkillProbability(SubSkillType.ACROBATICS_ROLL, mmoPlayer);
            final boolean rngSuccess = ProbabilityUtil.isStaticSkillRNGSuccessful(
                    PrimarySkillType.ACROBATICS, mmoPlayer, probability);

            final RollResult result = rollCheck(baseDamage, isGraceful, rngSuccess);
            if (result == null) {
                return null; // fatal fall — mcMMO must not interfere
            }
            if (!result.isExploiting() && result.getXpGain() > 0) {
                applyXpGain(result.getXpGain(), XPGainReason.PVE, XPGainSource.SELF);
            }
            // The player survived, so remember this landing block for the exploit throttle.
            addFallLocation();
            return result;
        }

        // Fall XP is granted even without the Roll subskill unlocked (singleplayer always permits the
        // skill). No damage reduction and no feedback in this branch.
        applyXpGain(calculateRollXP(baseDamage, false), XPGainReason.PVE, XPGainSource.SELF);
        return null;
    }

    /**
     * Evaluates a fall against the Roll mechanic. Deterministic given the pre-computed RNG outcome, so
     * it is unit-testable (the RNG roll itself is made by {@link #processFallDamage}).
     *
     * @param baseDamage the incoming fall damage
     * @param isGraceful whether the player was sneaking (Graceful Roll)
     * @param rngSuccess whether the skill RNG roll succeeded this fall
     * @return the outcome, or {@code null} when the fall is fatal even after any reduction
     */
    public @Nullable RollResult rollCheck(double baseDamage, boolean isGraceful, boolean rngSuccess) {
        final double threshold = McMMOMod.getAdvancedConfig().getRollDamageThreshold() * 2;
        final double modifiedDamage = Acrobatics.calculateModifiedRollDamage(baseDamage, threshold);
        final boolean isExploiting = isPlayerExploitingAcrobatics();

        // They rolled: the reduced hit is survivable and the roll proc'd.
        if (!isFatal(modifiedDamage) && rngSuccess) {
            float xpGain = 0;
            if (!isExploiting && canGainRollXP()) {
                xpGain = (int) calculateRollXP(baseDamage, true);
            }
            return new RollResult(true, isGraceful, modifiedDamage, isExploiting, xpGain);
        } else if (!isFatal(baseDamage)) {
            // They did not roll, but survived the fall — still reward XP as appropriate.
            float xpGain = 0;
            if (!isExploiting && canGainRollXP()) {
                xpGain = (int) calculateRollXP(baseDamage, false);
            }
            return new RollResult(false, isGraceful, modifiedDamage, isExploiting, xpGain);
        }

        // Fall was fatal.
        return null;
    }

    /**
     * Whether the player may gain Roll XP right now. When exploit prevention is off this is always
     * true; when on, it enforces a cooldown that lengthens with every early retry so a player cannot
     * farm XP by spamming fall damage.
     *
     * @return {@code true} if Roll XP may be awarded this call
     */
    public boolean canGainRollXP() {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return true;
        }

        if (System.currentTimeMillis() >= rollXPCooldown) {
            rollXPCooldown = System.currentTimeMillis() + rollXPInterval;
            rollXPIntervalLengthen = (1000 * 10);
            return true;
        } else {
            rollXPCooldown += rollXPIntervalLengthen;
            rollXPIntervalLengthen += 1000; // Add another second to the next penalty
            return false;
        }
    }

    /**
     * Whether the Roll subskill is unlocked and enabled for this player. Singleplayer permission is
     * always granted; the rank gate is the real check.
     */
    public boolean canRoll() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.ACROBATICS_ROLL)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.ACROBATICS_ROLL);
    }

    /**
     * Whether the Dodge subskill can fire right now: the player is not raising a shield, and the
     * subskill is unlocked and enabled. The legacy {@code canCombatSkillsTrigger} guard is always true
     * in singleplayer, and the lightning-dodge exclusion is applied by the listener (it holds the
     * attacker entity), so neither is checked here.
     */
    public boolean canDodge() {
        if (getPlayer().isBlocking()) {
            return false;
        }
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.ACROBATICS_DODGE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.ACROBATICS_DODGE);
    }

    /**
     * Processes an incoming combat hit against this player: rolls Dodge, and on success awards
     * Acrobatics XP (when the attacker is eligible) and hands back the reduced damage. Called from the
     * damage listener with the (post-armor/protection) combat damage.
     *
     * <p>Dodge XP is additionally suppressed for {@link Misc#PLAYER_RESPAWN_COOLDOWN_SECONDS} after
     * the player respawns (legacy's {@code cooldownExpired(mmoPlayer.getRespawnATS(), ...)} guard):
     * a fresh respawn is a cheap way to reset the per-mob dodge-XP tracker, so the grace period
     * closes that loop. Damage reduction is deliberately NOT gated — only the payout is, exactly as
     * upstream. (Legacy's other consumer of this timestamp is the PvP combat-XP branch, which is
     * unreachable in singleplayer.)
     *
     * @param baseDamage        the incoming combat damage
     * @param attackerXpEligible whether the attacker may grant dodge XP (the listener resolves this:
     *                          the attacker is a mob, under the per-mob dodge-XP cap); a successful
     *                          dodge still reduces damage when this is {@code false}, it just pays no XP
     * @return the {@link DodgeResult} on a successful dodge (the caller applies
     *         {@link DodgeResult#getModifiedDamage()} + feedback), or {@code null} when Dodge is locked,
     *         the roll fails, or the reduced hit would still be fatal
     */
    public @Nullable DodgeResult processDodge(double baseDamage, boolean attackerXpEligible) {
        if (!canDodge()) {
            return null;
        }
        final boolean rngSuccess = ProbabilityUtil.isSkillRNGSuccessful(
                SubSkillType.ACROBATICS_DODGE, mmoPlayer);
        final DodgeResult result = dodgeCheck(baseDamage, rngSuccess,
                attackerXpEligible && isRespawnGracePeriodOver());
        if (result != null && result.getXpGain() > 0) {
            applyXpGain(result.getXpGain(), XPGainReason.PVE, XPGainSource.SELF);
        }
        return result;
    }

    /**
     * Whether this player is far enough past their last respawn to be paid Dodge XP again (legacy
     * {@code SkillUtils.cooldownExpired(mmoPlayer.getRespawnATS(), Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS)}).
     *
     * <p>Split out of {@link #processDodge} so the gate is provable without the skill RNG, which has
     * no injection seam. Also true on a fresh login — {@code McMMOPlayer}'s constructor stamps the
     * timestamp, exactly as legacy's profile-loading task did.
     *
     * @return {@code true} once {@link Misc#PLAYER_RESPAWN_COOLDOWN_SECONDS} have elapsed
     */
    public boolean isRespawnGracePeriodOver() {
        return SkillUtils.cooldownExpired(mmoPlayer.getRespawnATS(),
                Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS);
    }

    /**
     * Evaluates a combat hit against the Dodge mechanic. Deterministic given the pre-computed RNG
     * outcome, so it is unit-testable (the RNG roll itself is made by {@link #processDodge}).
     *
     * @param baseDamage        the incoming combat damage
     * @param rngSuccess        whether the skill RNG roll succeeded this hit
     * @param attackerXpEligible whether the attacker may grant dodge XP (see {@link #processDodge})
     * @return the outcome on a successful dodge, or {@code null} when the roll failed or the reduced
     *         hit would still be fatal (mcMMO must not soften a killing blow into a survivable one)
     */
    public @Nullable DodgeResult dodgeCheck(double baseDamage, boolean rngSuccess,
            boolean attackerXpEligible) {
        final double modifiedDamage = Acrobatics.calculateModifiedDodgeDamage(baseDamage,
                McMMOMod.getAdvancedConfig().getDodgeDamageModifier());

        if (isFatal(modifiedDamage) || !rngSuccess) {
            return null;
        }

        final float xpGain = attackerXpEligible
                ? (float) (baseDamage * McMMOMod.getExperienceConfig().getDodgeXPModifier())
                : 0F;
        return new DodgeResult(modifiedDamage, xpGain);
    }

    /**
     * Computes the XP for a fall. Damage is clamped to 20 (guards against absurd damage-reduction
     * setups), scaled by the roll or fall modifier, and boosted when the player wears Feather Falling
     * boots. Verbatim legacy math.
     *
     * @param damage the survived fall damage
     * @param isRoll {@code true} for a successful roll (roll modifier), {@code false} for a plain fall
     * @return the XP to award
     */
    public float calculateRollXP(double damage, boolean isRoll) {
        damage = Math.min(20, damage);
        float xp = (float) (damage * (isRoll
                ? McMMOMod.getExperienceConfig().getRollXPModifier()
                : McMMOMod.getExperienceConfig().getFallXPModifier()));

        if (getPlayer().hasFeatherFallingBoots()) {
            xp *= McMMOMod.getExperienceConfig().getFeatherFallXPModifier();
        }

        return xp;
    }

    /**
     * Detects players farming Acrobatics XP: prevention must be enabled, and the player is exploiting
     * if they hold an Ender Pearl, are riding an entity, or are landing on a block they already fell
     * onto recently.
     */
    private boolean isPlayerExploitingAcrobatics() {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return false;
        }

        final PlatformPlayer player = getPlayer();
        // PORT: legacy also emitted a debug chat line describing which check tripped — dropped (debug
        // UX only; would drag Text/LocaleLoader into this MC-free core).
        if (player.hasEnderPearlInEitherHand() || player.isInsideVehicle()) {
            return true;
        }

        return fallLocationMap.contains(player.getFeetBlockKey());
    }

    /** Records the player's current feet block in the fall history (exploit throttle). */
    public void addFallLocation() {
        fallLocationMap.add(getPlayer().getFeetBlockKey());
    }

    private boolean isFatal(double damage) {
        return getPlayer().getHealth() - damage <= 0;
    }
}
