package com.gmail.nossr50.skills.agility;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.BlockLocationHistory;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.subskills.agility.DodgeResult;
import com.gmail.nossr50.datatypes.skills.subskills.agility.RollResult;
import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.Probability;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.DoublePredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Agility skill manager. Both the <b>Roll / Graceful Roll</b> fall-damage path (K2) and the
 * <b>Dodge</b> combat path (K1) are live, driven by the {@code modifyAppliedDamage} damage mixin via
 * {@link com.gmail.nossr50.fabric.listeners.EntityDamageListener}. The Dodge XP anti-farm cap
 * (per-mob dodge tracker) and the lightning-dodge exclusion live in the listener (the MC-typed layer,
 * which has the attacker entity); this manager takes the attacker's XP-eligibility as a boolean so its
 * damage-reduction + XP math stays deterministic and unit-testable.
 *
 * <p>Port note: the legacy Roll logic lived in the {@code AbstractSubSkill}-based
 * {@code datatypes.skills.subskills.agility.Roll}, tightly coupled to Bukkit's
 * {@code EntityDamageEvent}. It is folded into this manager MC-free: the RNG orchestration
 * ({@link #processFallDamage}) is verified in-game, while the deterministic pieces
 * ({@link #rollCheck}, {@link #calculateRollXP}, the exploit gate, and the fall-location throttle)
 * are unit-testable. Sound/notification feedback is emitted by the listener (the MC-typed layer), not
 * here, so this class imports no Minecraft types.
 *
 * <p><b>Agility earns no XP of its own.</b> It is a child skill: it owns all ten sub-skills and its
 * level — which is what every rank gate reads — is the mean of {@code PARKOUR}, {@code SWIMMING} and
 * {@code FLYING}. So every award in this class names its destination explicitly rather than going
 * through {@link SkillManager#applyXpGain}, which would target {@code AGILITY} and be split evenly
 * across all three parents by {@code McMMOPlayer#beginXpGain}. Travel pays the medium's own skill
 * ({@link Medium#primarySkill()}); falling and dodging pay {@link #EPISODIC_XP_SKILL}.
 */
public class AgilityManager extends SkillManager {

    /**
     * Where the Fall domain's XP goes — Roll, Graceful Roll and Dodge.
     *
     * <p>Agility cannot hold it (a child skill earns nothing) and splitting it three ways would mean
     * falling off a cliff trains your swimming. Landing well is a land-movement skill, so it pays
     * Parkour. Consequence worth knowing: a player who only ever flies gets nothing from Roll or
     * Dodge, and since Agility is the mean of three, their perks stall at level 333.
     */
    public static final PrimarySkillType EPISODIC_XP_SKILL = PrimarySkillType.PARKOUR;

    public AgilityManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.AGILITY);
        this.fallLocationMap = new BlockLocationHistory(50);
    }

    private long rollXPCooldown = 0;
    private final long rollXPInterval = (1000 * 3);
    private long rollXPIntervalLengthen = (1000 * 10);
    private final BlockLocationHistory fallLocationMap;

    // --- Movement domains (Pass 2) -------------------------------------------------------------

    /**
     * Snapshot of the movement-XP tuning, built on the first movement tick rather than in the
     * constructor.
     *
     * <p>Lazy for two reasons that pull the same way: managers are constructed before any config is
     * guaranteed wired (and unit tests build them with no config at all), and this is read 20×/s so
     * it must not be a live YAML walk — the Alchemy Catalysis per-tick-config-read trap. A manager
     * lives exactly as long as one player session, which is exactly as long as one loaded config, so
     * caching for the manager's lifetime is correct rather than merely convenient.
     */
    private MovementXpSettings movementXpSettings;

    /**
     * Fractional movement XP not yet handed to the XP pipeline, <b>per medium</b>.
     *
     * <p>A tick of travel is worth well under one XP, and pushing a fraction through
     * {@code beginXpGain} every tick would churn the level-up check, the diminished-returns ledger
     * and the profile dirty flag 20×/s for nothing. Whole XP is flushed; the remainder rides along
     * to the next tick so nothing is lost to truncation.
     *
     * <p>It is keyed by medium because each one now pays a <em>different</em> skill. A single shared
     * remainder would let a fraction of a second's swimming be flushed into Parkour the moment the
     * player climbed out of the water and sprinted — small, but wrong every time the medium changes,
     * which is constantly.
     */
    private final double[] movementXpAccumulators = new double[Medium.values().length];

    /**
     * Fractional Lead Lungs air, for the same reason: air is an integer counter, so a sub-1 top-up
     * per tick has to accumulate or it would floor to zero forever and the sub-skill would do
     * nothing at all.
     */
    private double leadLungsAirAccumulator;

    /**
     * Processes an incoming fall-damage hit: rolls for Roll/Graceful Roll, awards Agility XP, and
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
                    : ProbabilityUtil.getSubSkillProbability(SubSkillType.PARKOUR_ROLL, mmoPlayer);
            final boolean rngSuccess = ProbabilityUtil.isStaticSkillRNGSuccessful(
                    PrimarySkillType.AGILITY, mmoPlayer, probability);

            final RollResult result = rollCheck(baseDamage, isGraceful, rngSuccess);
            if (result == null) {
                return null; // fatal fall — mcMMO must not interfere
            }
            if (!result.isExploiting() && result.getXpGain() > 0) {
                applyEpisodicXpGain(result.getXpGain());
            }
            // The player survived, so remember this landing block for the exploit throttle.
            addFallLocation();
            return result;
        }

        // Fall XP is granted even without the Roll subskill unlocked (singleplayer always permits the
        // skill). No damage reduction and no feedback in this branch.
        applyEpisodicXpGain(calculateRollXP(baseDamage, false));
        return null;
    }

    /**
     * Pay Fall-domain XP (Roll / Graceful Roll / Dodge) into {@link #EPISODIC_XP_SKILL}.
     *
     * <p>Deliberately <em>not</em> {@link SkillManager#applyXpGain}: that targets this manager's own
     * skill, and Agility is a child skill, so the gain would be quietly split three ways and train
     * swimming and flying off a fall.
     */
    private void applyEpisodicXpGain(float xp) {
        if (xp <= 0) {
            return;
        }
        mmoPlayer.beginXpGain(EPISODIC_XP_SKILL, xp, XPGainReason.PVE, XPGainSource.SELF);
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
        final double modifiedDamage = Agility.calculateModifiedRollDamage(baseDamage, threshold);
        final boolean isExploiting = isPlayerExploitingAgility();

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
        if (!McMMOMod.getExperienceConfig().isAgilityExploitingPrevented()) {
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
     * always granted, and {@link SubSkillType#PARKOUR_ROLL} carries zero ranks (so
     * {@code getRank} returns {@code -1} = "always unlocked"), which makes this unconditionally
     * true today. It is kept as a real check rather than collapsed to {@code true} because the odds
     * — not the unlock — are the gate, and a future rank ladder would want exactly this call.
     *
     * <p>Roll lives on {@link PrimarySkillType#PARKOUR} rather than on this manager's own skill; see
     * {@link SubSkillType#PARKOUR_ROLL} for why. The manager still owns the behaviour, because every
     * movement sub-skill does.
     */
    public boolean canRoll() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.PARKOUR_ROLL)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.PARKOUR_ROLL);
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
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.PARKOUR_DODGE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.PARKOUR_DODGE);
    }

    /**
     * Processes an incoming combat hit against this player: rolls Dodge, and on success awards
     * Agility XP (when the attacker is eligible) and hands back the reduced damage. Called from the
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
                SubSkillType.PARKOUR_DODGE, mmoPlayer);
        final DodgeResult result = dodgeCheck(baseDamage, rngSuccess,
                attackerXpEligible && isRespawnGracePeriodOver());
        if (result != null && result.getXpGain() > 0) {
            applyEpisodicXpGain(result.getXpGain());
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
        final double modifiedDamage = Agility.calculateModifiedDodgeDamage(baseDamage,
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
     * Detects players farming Agility XP: prevention must be enabled, and the player is exploiting
     * if they hold an Ender Pearl, are riding an entity, or are landing on a block they already fell
     * onto recently.
     */
    private boolean isPlayerExploitingAgility() {
        if (!McMMOMod.getExperienceConfig().isAgilityExploitingPrevented()) {
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

    // ==========================================================================================
    //  Movement domains — Land, Water, Air (Pass 2)
    // ==========================================================================================

    /** The movement-XP tuning for this session, snapshotted on first use. */
    public @NotNull MovementXpSettings movementXpSettings() {
        MovementXpSettings local = movementXpSettings;
        if (local == null) {
            local = MovementXpSettings.fromConfig();
            movementXpSettings = local;
        }
        return local;
    }

    /** Test seam: install an explicit tuning snapshot instead of reading the live config. */
    public void setMovementXpSettings(@NotNull MovementXpSettings settings) {
        this.movementXpSettings = settings;
    }

    /**
     * Credit one tick of travel through {@code medium}.
     *
     * <p>The single entry point for all three new domains — the whole point of merging Sprinting,
     * Swimming and Flying into one skill is that there is one accumulator and one guard set here
     * instead of three. The caller (F1) has already established that this tick's movement is
     * legitimate (not in a vehicle, not a teleport, actually moved); this method owns the clamp and
     * the payout.
     *
     * <p>The XP is paid into {@link Medium#primarySkill()} — Parkour, Swimming or Flying — never
     * into Agility, whose level is derived from those three.
     *
     * @param medium   the medium travelled, already resolved by the caller
     * @param distance horizontal distance moved this tick, in blocks
     * @return the whole XP awarded this tick — usually {@code 0}, since a tick is worth a fraction
     *         of one XP and the remainder is accumulated
     */
    public float onMovementTick(@NotNull Medium medium, double distance) {
        final int slot = medium.ordinal();
        movementXpAccumulators[slot] += movementXpSettings().xpFor(medium, distance);
        if (movementXpAccumulators[slot] < 1.0) {
            return 0F;
        }
        final float whole = (float) Math.floor(movementXpAccumulators[slot]);
        movementXpAccumulators[slot] -= whole;
        mmoPlayer.beginXpGain(medium.primarySkill(), whole, XPGainReason.PVE, XPGainSource.SELF);
        return whole;
    }

    /**
     * Seconds of travel credited for a tick's distance — the speed clamp. Delegates to
     * {@link MovementXpSettings#creditedSeconds}; exposed here because it is the single most
     * important thing in the skill to be able to assert on.
     */
    public double creditedSeconds(@NotNull Medium medium, double distance) {
        return movementXpSettings().creditedSeconds(medium, distance);
    }

    // --- Sub-skill 3: Fleet Footed ------------------------------------------------------------

    /**
     * Whether this medium's Fleet Footed rank is unlocked (land at rank 1, water 2, air 3).
     */
    public boolean canFleetFoot(@NotNull Medium medium) {
        return RankUtils.hasReachedRank(medium.fleetFootedRank(), mmoPlayer,
                SubSkillType.AGILITY_FLEET_FOOTED)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.AGILITY_FLEET_FOOTED);
    }

    /**
     * The speed bonus to apply while travelling through {@code medium}, or {@code 0} when that
     * medium's rank is not yet unlocked.
     *
     * <p>The units differ per medium and that asymmetry is deliberate rather than sloppy — see
     * {@link com.gmail.nossr50.platform.SkillAttributeService.Managed}. Land is a movement-speed
     * fraction, water is a flat addition to water movement efficiency (because movement speed does
     * not move a swimmer), and air is a velocity nudge factor (because elytra flight has no
     * attribute at all). One sub-skill, one rank ladder, one config block — two application
     * mechanisms.
     *
     * @param medium the medium being travelled
     * @return the bonus, scaled linearly with level to the configured cap
     */
    public double getFleetFootedBonus(@NotNull Medium medium) {
        if (!canFleetFoot(medium)) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return 0.0;
        }
        return scaleToLevel(advanced.getFleetFootedMaxBonus(medium),
                advanced.getFleetFootedMaxBonusLevel());
    }

    // --- Sub-skill 4: Athlete -----------------------------------------------------------------

    public boolean canAthlete() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.PARKOUR_ATHLETE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.PARKOUR_ATHLETE);
    }

    /**
     * The factor to scale sprint exhaustion by — below 1 means sprinting costs less hunger.
     *
     * <p>Never returns 0, and cannot be configured to: a multiplier of zero would make sprinting
     * literally free, which removes food from the game for anyone who levels this skill. The
     * configured reduction is clamped into {@code [0, 0.95]} before it is subtracted, so the worst
     * case is "sprinting costs 5% of normal", not "costs nothing".
     *
     * @return a multiplier in {@code (0, 1]}
     */
    public double getAthleteExhaustionMultiplier() {
        if (!canAthlete()) {
            return 1.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return 1.0;
        }
        final double cap = Math.min(0.95, Math.max(0.0, advanced.getAthleteMaxExhaustionReduction()));
        return 1.0 - scaleToLevel(cap, advanced.getAthleteMaxBonusLevel());
    }

    // --- Sub-skill 5: Smash -------------------------------------------------------------------

    public boolean canSmash() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.PARKOUR_SMASH)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.PARKOUR_SMASH);
    }

    /** Rolls Smash for a sprint-attack. The caller has already checked the player is sprinting. */
    public boolean rollSmash() {
        return canSmash()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.PARKOUR_SMASH, mmoPlayer);
    }

    /** Bonus damage a successful Smash adds to a sprint-attack. */
    public double getSmashBonusDamage() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null ? 0.0 : advanced.getSmashBonusDamage();
    }

    /** Extra knockback a successful Smash applies. */
    public double getSmashKnockback() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null ? 0.0 : advanced.getSmashKnockbackStrength();
    }

    // --- Sub-skill 6: Lead Lungs --------------------------------------------------------------

    public boolean canLeadLungs() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWIMMING_LEAD_LUNGS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWIMMING_LEAD_LUNGS);
    }

    /**
     * Air ticks restored per submerged tick, before accumulation.
     *
     * <p>Vanilla drains exactly one air per tick, so a value of 1.0 would be literally infinite
     * breath. The configured cap is clamped below that on purpose: Lead Lungs should approach
     * Respiration III territory without trivially exceeding it.
     *
     * @return the per-tick top-up, in the {@code [0, 0.95]} range
     */
    public double getLeadLungsAirTopUpPerTick() {
        if (!canLeadLungs()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return 0.0;
        }
        final double cap = Math.min(0.95,
                Math.max(0.0, advanced.getLeadLungsMaxAirTopUpPerTick()));
        return scaleToLevel(cap, advanced.getLeadLungsMaxBonusLevel());
    }

    /**
     * Accumulate this tick's Lead Lungs top-up and hand back whole air ticks to restore.
     *
     * <p>Air is an integer counter, so the fractional per-tick value has to be banked — flooring it
     * every tick would return 0 forever and the sub-skill would silently do nothing.
     *
     * @return whole air ticks to add this tick, usually {@code 0} or {@code 1}
     */
    public int consumeLeadLungsAirTopUp() {
        final double perTick = getLeadLungsAirTopUpPerTick();
        if (perTick <= 0) {
            leadLungsAirAccumulator = 0;
            return 0;
        }
        leadLungsAirAccumulator += perTick;
        if (leadLungsAirAccumulator < 1.0) {
            return 0;
        }
        final int whole = (int) Math.floor(leadLungsAirAccumulator);
        leadLungsAirAccumulator -= whole;
        return whole;
    }

    // --- Sub-skill 7: Second Wind --------------------------------------------------------------

    /**
     * Whether the Second Wind body for {@code medium} is unlocked. The three bodies share the
     * ability's three ranks in medium order, so a level-250 player has the land lunge only.
     */
    public boolean canSecondWind(@NotNull Medium medium) {
        return RankUtils.hasReachedRank(medium.fleetFootedRank(), mmoPlayer,
                SubSkillType.AGILITY_SECOND_WIND)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.AGILITY_SECOND_WIND);
    }

    /**
     * Resolve the Second Wind effect for the medium the player is moving through, or {@code null}
     * when that body is not unlocked.
     *
     * <p>Returning {@code null} rather than a zeroed result is load-bearing: the caller must be able
     * to refuse the activation <em>without burning the cooldown</em>, and an all-zeros result is
     * indistinguishable from a legitimately weak one.
     *
     * @param medium the medium the player is currently moving through
     * @param durationTicks the ability duration the super-ability infra computed for this player
     * @return the resolved effect, or {@code null} if this body is locked
     */
    public @Nullable SecondWindResult computeSecondWind(@NotNull Medium medium, int durationTicks) {
        if (!canSecondWind(medium)) {
            return null;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return null;
        }
        return switch (medium) {
            // Dart: an instantaneous lunge, so no duration — the magnitude is the launch velocity.
            case LAND -> new SecondWindResult(medium, 0, advanced.getSecondWindDartKnockback(),
                    advanced.getSecondWindDartRange(), advanced.getSecondWindDartDamage(),
                    advanced.getSecondWindDartKnockback());
            // Aquaman: a timed buff; the magnitude is the status-effect amplifier.
            case WATER -> new SecondWindResult(medium, durationTicks,
                    advanced.getSecondWindAquamanAmplifier(), 0, 0, 0);
            // Limitless: a timed boost; the magnitude multiplies forward velocity.
            case AIR -> new SecondWindResult(medium, durationTicks,
                    advanced.getSecondWindLimitlessBoost(), 0, 0, 0);
        };
    }

    // --- Sub-skill 8: Glide -------------------------------------------------------------------

    public boolean canGlide() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.FLYING_GLIDE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FLYING_GLIDE);
    }

    /**
     * The fraction by which to reduce downward velocity while gliding.
     *
     * <p>Clamped strictly below 1 so a maxed player still descends — a reduction of 1.0 would pin
     * them at a fixed altitude and make landing impossible.
     *
     * @return a reduction in {@code [0, 0.9]}
     */
    public double getGlideDescentReduction() {
        if (!canGlide()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return 0.0;
        }
        final double cap = Math.min(0.9, Math.max(0.0, advanced.getGlideMaxDescentReduction()));
        return scaleToLevel(cap, advanced.getGlideMaxBonusLevel());
    }

    // --- Sub-skill 9: Lake Raider -------------------------------------------------------------

    public boolean canLakeRaider() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWIMMING_LAKE_RAIDER)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWIMMING_LAKE_RAIDER);
    }

    /**
     * The main Lake Raider roll for an underwater block break — the primary gate, before any
     * individual treasure's own drop chance is consulted.
     */
    public boolean rollLakeRaiderSuccess() {
        return canLakeRaider()
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.SWIMMING_LAKE_RAIDER, mmoPlayer);
    }

    /**
     * The MC-free treasure-selection core of Lake Raider, shaped exactly like
     * {@link com.gmail.nossr50.skills.herbalism.HerbalismManager#rollHylianLuck}: both random draws
     * are supplied by the caller, so the whole selection is unit-testable with pinned RNG.
     *
     * <p>It reuses the <em>Excavation</em> treasure tables rather than shipping a parallel one.
     * That is a deliberate reuse, not laziness: the blocks a player breaks underwater are sand,
     * gravel, clay and dirt — precisely the blocks those tables already describe — and a second table
     * of the same loot keyed by the same blocks would be a maintenance trap where the two drift
     * apart. The difference is the gate, not the loot: Lake Raider ignores the treasures'
     * Excavation-level requirement, because it is an Agility perk being paid for Agility levels.
     *
     * @param candidates  the broken block's excavation treasures, in config order
     * @param mainRollWon whether the {@code SWIMMING_LAKE_RAIDER} sub-skill roll succeeded
     * @param staticRoll  given a treasure's {@code Drop_Chance} (0–100), whether its static roll wins
     * @return the treasure to drop, or empty if none was won
     */
    public @NotNull Optional<ExcavationTreasure> rollLakeRaiderTreasure(
            @NotNull List<ExcavationTreasure> candidates, boolean mainRollWon,
            @NotNull DoublePredicate staticRoll) {
        if (!mainRollWon || candidates.isEmpty()) {
            return Optional.empty();
        }
        for (ExcavationTreasure treasure : candidates) {
            if (staticRoll.test(treasure.getDropChance())) {
                return Optional.of(treasure);
            }
        }
        return Optional.empty();
    }

    // --- Sub-skill 10: Solar Wings ------------------------------------------------------------

    public boolean canSolarWings() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.FLYING_SOLAR_WINGS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.FLYING_SOLAR_WINGS);
    }

    /**
     * Durability restored per Solar Wings interval, doubled (by config) when the player is standing
     * on the ground rather than flying.
     *
     * @param grounded whether the player is on the ground
     * @return the durability points to repair this interval
     */
    public int getSolarWingsRepairAmount(boolean grounded) {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null || !canSolarWings()) {
            return 0;
        }
        final int base = Math.max(0, advanced.getSolarWingsRepairPerInterval());
        return grounded ? base * Math.max(1, advanced.getSolarWingsGroundedMultiplier()) : base;
    }

    /** How often Solar Wings ticks, in server ticks. Floored at 1 so it can never divide by zero. */
    public int getSolarWingsIntervalTicks() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null ? 100 : Math.max(1, advanced.getSolarWingsIntervalTicks());
    }

    // --- Sub-skill 11: Snow Walker (a PARKOUR sub-skill, not an Agility one) ---------------------

    /**
     * Whether the player can cross powder snow without sinking into it.
     *
     * <p><b>Gated on Parkour, not on Agility</b>, and that is the whole point of it living under
     * {@link SubSkillType#PARKOUR_SNOW_WALKER}: the parent-skill map keys off the enum name's
     * prefix, so this reads the {@code PARKOUR} level directly rather than the mean of Parkour,
     * Swimming and Flying that every other sub-skill here reads. Not falling through snow is a
     * running-and-jumping perk; a strong swimmer should not be handed it by the average.
     *
     * <p>It lives on this manager anyway because every movement sub-skill does, and splitting one
     * out into a {@code ParkourManager} would mean a second manager, a second lazy-construction
     * site and a second place to look for movement behaviour — for one boolean.
     *
     * <p>Effect equivalence: vanilla already lets a player in leather boots walk on powder snow
     * ({@code PowderSnowBlock#canWalkOnPowderSnow}). This grants exactly that, so it stacks with
     * nothing and there is no way for it to be worth more than the boots already are.
     */
    public boolean canSnowWalk() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.PARKOUR_SNOW_WALKER)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.PARKOUR_SNOW_WALKER);
    }

    // Note: the level-scaling ladder these sub-skills share (`scaleToLevel`) lives on
    // {@link SkillManager}, since Stealth's passives are shaped identically.
}
