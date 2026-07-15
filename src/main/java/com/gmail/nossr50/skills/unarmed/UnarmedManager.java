package com.gmail.nossr50.skills.unarmed;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Unarmed skill manager. <b>Complete</b>: every Unarmed sub-skill that can fire in singleplayer now
 * has a live decision core.
 *
 * <p>Kept (config + rank arithmetic, server-free and provable):
 * <ul>
 *   <li>{@link #berserkDamage(double)} — the Berserk flat damage boost, scaled by the captured
 *       attack-cooldown charge (see {@link McMMOPlayer#getAttackStrength()});</li>
 *   <li>{@link #getSteelArmStyleDamage()} / {@link #calculateSteelArmStyleDamage()} — the Steel Arm
 *       Style rank bonus, with the config override path;</li>
 *   <li>{@link #canUseBlockCracker()} / {@link #rollBlockCracker()} — the Block Cracker gates. Legacy's
 *       {@code blockCrackerCheck} also mutated the block; that half is split out into the MC-free
 *       {@link Unarmed#blockCrackerConversionTarget} table plus the listener's live block swap;</li>
 *   <li>{@link #canDeflect()} / {@link #rollArrowDeflect()} — the Arrow Deflect gates. The cancel of
 *       the incoming hit and the notification live in {@code fabric.listeners.EntityDamageListener};</li>
 *   <li>the activation/unlock gates.</li>
 * </ul>
 *
 * <p><b>Deliberately not ported — Disarm and Iron Grip are unreachable in singleplayer</b> (the same
 * honest collapse as {@code CombatUtils#shouldBeAffected}'s player arm, and the reason
 * {@code safeDealDamage}'s no-attacker overload was left out: porting an unreachable branch is how
 * the §F dead-code bugs are made):
 * <ul>
 *   <li>{@code canDisarm(LivingEntity target)} requires {@code target instanceof Player}, and its
 *       only caller ({@code CombatUtils#processUnarmedCombat}) passes the entity the player just
 *       <em>swung at</em>. The attacker is the only player here, and nothing melees itself, so the
 *       gate is never true ⇒ {@code disarmCheck} is dead. With it go
 *       {@code ItemSpawnReason.UNARMED_DISARMED_ITEM}, {@code METADATA_KEY_DISARMED_ITEM} and the
 *       {@code Disarm.AntiTheft} config, which exist only to serve it.</li>
 *   <li>{@code hasIronGrip(Player defender)} is called from exactly one place — inside
 *       {@code disarmCheck} — and defends a player against <em>being</em> disarmed. Only an mcMMO
 *       player disarms anyone; mobs never do. Dead for the same reason.</li>
 * </ul>
 * Both remain listed in {@code SubSkillType} and in the skill's command output, exactly as the
 * dropped PvP arms elsewhere do — they simply never fire.
 */
public class UnarmedManager extends SkillManager {
    public static final double BERSERK_DMG_MODIFIER = 1.5;

    public UnarmedManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.UNARMED);
    }

    public boolean canActivateAbility() {
        return mmoPlayer.getToolPreparationMode(ToolType.FISTS) && Permissions.berserk(getPlayer());
    }

    public boolean canUseSteelArm() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.UNARMED_STEEL_ARM_STYLE)) {
            return false;
        }

        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.UNARMED_STEEL_ARM_STYLE);
    }

    public boolean canUseBerserk() {
        return mmoPlayer.getAbilityMode(SuperAbilityType.BERSERK);
    }

    /**
     * Arrow Deflect: whether the player is in a position to swat an incoming arrow away — unlocked,
     * enabled, and bare-handed. Ports legacy {@code UnarmedManager#canDeflect}, whose held-item read
     * becomes {@link PlatformPlayer#isUnarmed()} so the whole gate stays MC-free.
     *
     * <p>Legacy's {@code projectile instanceof Arrow} half stays on the caller
     * ({@code fabric.listeners.EntityDamageListener}), which is where the MC types are — the same
     * split Counter Attack's {@code instanceof LivingEntity} half uses.
     */
    public boolean canDeflect() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.UNARMED_ARROW_DEFLECT)) {
            return false;
        }

        return getPlayer().isUnarmed()
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.UNARMED_ARROW_DEFLECT);
    }

    /**
     * Arrow Deflect: roll for the deflection landing. Ports the RNG half of legacy
     * {@code UnarmedManager#deflectCheck}; the caller owns the two MC-typed halves legacy did inline
     * — cancelling the hit and notifying the player.
     *
     * <p>Not scaled by attack strength: legacy passes no multiplier here, and rightly so — deflecting
     * an arrow is a reaction, not a swing of the player's own, so there is no cooldown charge to
     * scale by. (Same reasoning as Counter Attack, which is likewise unscaled.)
     *
     * @return {@code true} if the incoming arrow should be deflected
     */
    public boolean rollArrowDeflect() {
        return ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.UNARMED_ARROW_DEFLECT, mmoPlayer);
    }

    public boolean canUseBlockCracker() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.UNARMED_BLOCK_CRACKER)) {
            return false;
        }

        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.UNARMED_BLOCK_CRACKER);
    }

    /**
     * Whether this Berserk strike cracks the block it hit — the config + RNG half of legacy
     * {@code blockCrackerCheck}. The "what does it crack into" half is the MC-free table in
     * {@link Unarmed#blockCrackerConversionTarget}, and the live block swap belongs to the caller.
     *
     * @return {@code true} if the struck block should be converted to its cracked variant
     */
    public boolean rollBlockCracker() {
        if (!McMMOMod.getGeneralConfig().isBlockCrackerAllowed()) {
            return false;
        }

        return ProbabilityUtil.isNonRNGSkillActivationSuccessful(SubSkillType.UNARMED_BLOCK_CRACKER,
                mmoPlayer);
    }

    /**
     * Handle the effects of the Berserk ability.
     *
     * @param damage The amount of damage initially dealt by the event
     * @return the bonus damage Berserk adds on top of {@code damage}
     */
    public double berserkDamage(double damage) {
        damage = ((damage * BERSERK_DMG_MODIFIER) * mmoPlayer.getAttackStrength()) - damage;

        return damage;
    }

    /**
     * Handle the effects of the Iron Arm ability.
     *
     * @return the Steel Arm Style bonus damage if the RNG activation succeeds, otherwise {@code 0}
     */
    public double calculateSteelArmStyleDamage() {
        if (ProbabilityUtil.isNonRNGSkillActivationSuccessful(SubSkillType.UNARMED_STEEL_ARM_STYLE,
                mmoPlayer)) {
            return getSteelArmStyleDamage();
        }

        return 0;
    }

    public double getSteelArmStyleDamage() {
        double rank = RankUtils.getRank(getPlayer(), SubSkillType.UNARMED_STEEL_ARM_STYLE);

        double bonus = 0;

        if (rank >= 18) {
            bonus = 1 + rank - 18;
        }

        double finalBonus = bonus + 0.5 + (rank / 2);

        if (McMMOMod.getAdvancedConfig().isSteelArmDamageCustom()) {
            return McMMOMod.getAdvancedConfig().getSteelArmOverride(
                    RankUtils.getRank(getPlayer(), SubSkillType.UNARMED_STEEL_ARM_STYLE),
                    finalBonus);
        } else {
            return finalBonus;
        }
    }
}
