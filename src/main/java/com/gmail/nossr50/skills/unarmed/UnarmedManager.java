package com.gmail.nossr50.skills.unarmed;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Unarmed skill manager (Phase 10.3 port). Only the pure damage-math sub-skills and their unlock
 * gates survive; every combat-effect body that mutates a Bukkit entity, block, or inventory is
 * dropped until the combat/entity + metadata adapters land.
 *
 * <p>Kept (config + rank arithmetic, server-free and provable):
 * <ul>
 *   <li>{@link #berserkDamage(double)} — the Berserk flat damage boost, scaled by the captured
 *       attack-cooldown charge (see {@link McMMOPlayer#getAttackStrength()});</li>
 *   <li>{@link #getSteelArmStyleDamage()} / {@link #calculateSteelArmStyleDamage()} — the Steel Arm
 *       Style rank bonus, with the config override path;</li>
 *   <li>the activation/unlock gates.</li>
 * </ul>
 *
 * <p>Dropped until the combat phase (each needs a Bukkit surface with no adapter yet):
 * <ul>
 *   <li>{@code disarmCheck} / {@code canDisarm} / {@code hasIronGrip} — read the defender's held
 *       {@code ItemStack}, spawn a dropped {@code Item}, tag it with entity metadata, fire the
 *       disarm event, and push notifications;</li>
 *   <li>{@code deflectCheck} / {@code canDeflect} — inspect the held item (ItemUtils) and notify;</li>
 *   <li>{@code blockCrackerCheck} / {@code canUseBlockCracker} — mutate {@code Block} material.</li>
 * </ul>
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
