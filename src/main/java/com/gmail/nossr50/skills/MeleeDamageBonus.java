package com.gmail.nossr50.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import org.jetbrains.annotations.NotNull;

/**
 * K1 attacker branch, MC-free half: the melee on-hit damage-bonus composition pulled out of the
 * legacy {@code CombatUtils#processSwordCombat}/{@code processAxeCombat}/{@code processUnarmedCombat}/
 * {@code processMacesCombat}/{@code processTridentCombatMelee} so it's server-free and unit-testable.
 * The {@code fabric.listeners.EntityDamageListener} owns the
 * MC-typed half — resolving the attacker, confirming a direct melee swing, and classifying the held
 * item into a {@link MeleeWeapon} — then defers the actual damage arithmetic here.
 *
 * <p>The Axes arm additionally drives the sub-skills that need to inspect the target — Armor Impact,
 * Greater Impact and Critical Strikes — because their outcome feeds this same damage total and
 * legacy's ordering between them is load-bearing (see {@link #applyBonus}). They reach the entity
 * through the {@link PlatformLivingEntity} adapter, so this class stays server-free.
 *
 * <p>Only damage <em>contributions</em> belong here. The on-hit sub-skills that do not feed this
 * total — Rupture, the Serrated Strikes / Skull Splitter AoEs, Counter Attack, Maces Cripple, and
 * Spears Momentum (the one that buffs the <em>attacker</em> rather than touching the target) — live
 * in {@code EntityDamageListener}, as does per-hit combat XP. <b>Limit Break</b> does belong here and
 * is applied to all six melee weapons below, last in the chain — see {@link LimitBreak} for why the
 * armour-quality nerf table did not come with it. Disarm and Iron Grip are unreachable in
 * singleplayer and were removed outright, constants and all (see {@code SubSkillType}).
 */
public final class MeleeDamageBonus {

    /** The melee weapon classes that carry an on-hit damage bonus (plus the no-op fallback). */
    public enum MeleeWeapon {
        SWORD,
        AXE,
        MACE,
        TRIDENT,
        SPEAR,
        UNARMED,
        OTHER
    }

    private MeleeDamageBonus() {
    }

    /**
     * The post-armor damage after adding the attacker's melee weapon on-hit bonus. Faithful to the
     * legacy per-weapon dispatch: Swords adds Stab, Axes runs its whole chain (below), Unarmed adds
     * Steel Arm Style then Berserk (Berserk scales off the already-boosted damage). Every bonus is
     * scaled by the captured attack-cooldown charge ({@link McMMOPlayer#getAttackStrength()}),
     * exactly as legacy did. A null manager or unmet unlock/permission gate contributes nothing.
     *
     * <p>The Axes chain's order is legacy's and matters: Axe Mastery lands first, then <em>either</em>
     * Armor Impact (armored target — durability only, no damage) <em>or</em> Greater Impact (unarmored
     * — knockback plus flat bonus damage), and Critical Strikes last, multiplying the damage those
     * have already accumulated rather than the base hit.
     *
     * @param mmoPlayer     the attacking player's mcMMO profile
     * @param weapon        the classification of the held main-hand item
     * @param appliedDamage the vanilla post-armor damage that would be applied
     * @param target        the entity being hit, for the sub-skills that inspect or move it
     * @return the damage mcMMO wants applied instead (>= {@code appliedDamage})
     */
    public static float applyBonus(@NotNull McMMOPlayer mmoPlayer, @NotNull MeleeWeapon weapon,
            float appliedDamage, @NotNull PlatformLivingEntity target) {
        final float attackStrength = mmoPlayer.getAttackStrength();
        double boostedDamage = appliedDamage;

        switch (weapon) {
            case SWORD -> {
                final SwordsManager swords = mmoPlayer.getSwordsManager();
                if (swords != null && swords.canUseStab()) {
                    boostedDamage += swords.getStabDamage() * attackStrength;
                }
            }
            case AXE -> {
                final AxesManager axes = mmoPlayer.getAxesManager();
                if (axes != null) {
                    if (axes.canUseAxeMastery()) {
                        boostedDamage += axes.axeMastery() * attackStrength;
                    }
                    if (axes.canImpact(target)) {
                        axes.impactCheck(target);
                    } else if (axes.canGreaterImpact(target)) {
                        boostedDamage += axes.greaterImpact(target) * attackStrength;
                    }
                    if (axes.canCriticalHit(target)) {
                        boostedDamage += axes.criticalHit(boostedDamage) * attackStrength;
                    }
                }
            }
            case MACE -> {
                final MacesManager maces = mmoPlayer.getMacesManager();
                if (maces != null) {
                    // Crush is a flat rank-based bonus (getCrushDamage is 0 without the unlock), scaled
                    // by attack strength like every other melee bonus. Its Cripple on-hit effect is not
                    // a damage contribution, so it runs from EntityDamageListener, not here.
                    boostedDamage += maces.getCrushDamage() * attackStrength;
                }
            }
            case TRIDENT -> {
                final TridentsManager tridents = mmoPlayer.getTridentsManager();
                if (tridents != null && tridents.canImpale()) {
                    // The MELEE Impale bonus is scaled by attack strength; the ranged one (a thrown
                    // trident, in EntityDamageListener) is not — a throw has no swing to charge. That
                    // asymmetry is legacy's, preserved deliberately.
                    boostedDamage += tridents.impaleDamageBonus() * attackStrength;
                }
            }
            case SPEAR -> {
                final SpearsManager spears = mmoPlayer.getSpearsManager();
                if (spears != null && spears.canUseSpearMastery()) {
                    boostedDamage += spears.getSpearMasteryBonusDamage() * attackStrength;
                }
                // Momentum is a movement buff on the attacker, not a damage contribution, so it runs
                // from EntityDamageListener alongside Cripple — legacy's processSpearsCombat calls it
                // after event.setDamage() for the same reason.
            }
            case UNARMED -> {
                final UnarmedManager unarmed = mmoPlayer.getUnarmedManager();
                if (unarmed != null) {
                    if (unarmed.canUseSteelArm()) {
                        boostedDamage += unarmed.calculateSteelArmStyleDamage() * attackStrength;
                    }
                    if (unarmed.canUseBerserk()) {
                        boostedDamage += unarmed.berserkDamage(boostedDamage) * attackStrength;
                    }
                }
            }
            case OTHER -> {
                // Not a recognised melee weapon (bow held while punching, block in hand, …).
            }
        }

        // ⚠️ Limit Break lands LAST, outside the switch, and that placement is legacy's rather than
        // convenient: in every one of processSwordCombat / processAxeCombat / processUnarmedCombat /
        // processMacesCombat / processSpearsCombat / processTridentCombatMelee it is the final
        // addition before event.setDamage(). It matters because two bonuses above MULTIPLY the
        // running total rather than adding to it — Axes' Critical Strikes and Unarmed's Berserk both
        // scale `boostedDamage` — so moving Limit Break ahead of either would quietly inflate it.
        //
        // Keeping it here, sealed after the switch, is the structural fix rather than a comment
        // asking the next person not to get it wrong: there is no per-weapon arm left to append to,
        // and a new weapon gets Limit Break by adding one row to limitBreakOf(), not by remembering
        // to re-type this line. Same reasoning as sealing the diet chain inside applyDietBonus.
        final SubSkillType limitBreak = limitBreakOf(weapon);
        if (limitBreak != null) {
            boostedDamage += LimitBreak.bonusDamage(mmoPlayer, limitBreak) * attackStrength;
        }

        return (float) boostedDamage;
    }

    /**
     * The {@code *_LIMIT_BREAK} sub-skill a melee weapon class carries, or {@code null} for
     * {@link MeleeWeapon#OTHER}, which is not a weapon and has no Limit Break of its own.
     *
     * @param weapon the classification of the held main-hand item
     * @return the matching Limit Break sub-skill, or {@code null}
     */
    private static SubSkillType limitBreakOf(@NotNull MeleeWeapon weapon) {
        return switch (weapon) {
            case SWORD -> SubSkillType.SWORDS_SWORDS_LIMIT_BREAK;
            case AXE -> SubSkillType.AXES_AXES_LIMIT_BREAK;
            case MACE -> SubSkillType.MACES_MACES_LIMIT_BREAK;
            case TRIDENT -> SubSkillType.TRIDENTS_TRIDENTS_LIMIT_BREAK;
            case SPEAR -> SubSkillType.SPEARS_SPEARS_LIMIT_BREAK;
            case UNARMED -> SubSkillType.UNARMED_UNARMED_LIMIT_BREAK;
            case OTHER -> null;
        };
    }
}
