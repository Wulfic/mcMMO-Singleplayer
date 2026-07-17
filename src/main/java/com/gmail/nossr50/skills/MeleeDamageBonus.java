package com.gmail.nossr50.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import org.jetbrains.annotations.NotNull;

/**
 * K1 attacker branch, MC-free half: the melee on-hit damage-bonus composition pulled out of the
 * legacy {@code CombatUtils#processSwordCombat}/{@code processAxeCombat}/{@code processUnarmedCombat}/
 * {@code processMacesCombat} so it's server-free and unit-testable. The {@code fabric.listeners.EntityDamageListener} owns the
 * MC-typed half — resolving the attacker, confirming a direct melee swing, and classifying the held
 * item into a {@link MeleeWeapon} — then defers the actual damage arithmetic here.
 *
 * <p>The Axes arm additionally drives the sub-skills that need to inspect the target — Armor Impact,
 * Greater Impact and Critical Strikes — because their outcome feeds this same damage total and
 * legacy's ordering between them is load-bearing (see {@link #applyBonus}). They reach the entity
 * through the {@link PlatformLivingEntity} adapter, so this class stays server-free.
 *
 * <p>Still deferred behind their own adapters, matching the manager ports: Disarm, Arrow Deflect,
 * Taming's damage modifiers, and Limit Break (PvP-only). Rupture, the Serrated Strikes / Skull
 * Splitter AoEs, Counter Attack and Maces Cripple are live but sit in {@code EntityDamageListener}
 * instead — none of them contributes to the attacker's damage total, so they do not belong in this
 * composition.
 */
public final class MeleeDamageBonus {

    /** The melee weapon classes that carry an on-hit damage bonus (plus the no-op fallback). */
    public enum MeleeWeapon {
        SWORD,
        AXE,
        MACE,
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

        return (float) boostedDamage;
    }
}
