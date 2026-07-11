package com.gmail.nossr50.skills.acrobatics;

import com.gmail.nossr50.datatypes.BlockLocationHistory;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.Probability;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Acrobatics skill manager. The Dodge combat path is still deferred until the K1 combat-damage hook
 * lands (it needs attacker identity + mob metadata tracking); what is live here is the <b>Roll /
 * Graceful Roll</b> fall-damage path (K2), driven by the {@code modifyAppliedDamage} damage mixin via
 * {@link com.gmail.nossr50.fabric.listeners.EntityDamageListener}.
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
