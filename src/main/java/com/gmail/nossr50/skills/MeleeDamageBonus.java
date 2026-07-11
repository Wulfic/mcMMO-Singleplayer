package com.gmail.nossr50.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import org.jetbrains.annotations.NotNull;

/**
 * K1 attacker branch, MC-free half: the melee on-hit damage-bonus composition pulled out of the
 * legacy {@code CombatUtils#processSwordCombat}/{@code processAxeCombat}/{@code processUnarmedCombat}
 * so it's server-free and unit-testable. The {@code fabric.listeners.EntityDamageListener} owns the
 * MC-typed half — resolving the attacker, confirming a direct melee swing, and classifying the held
 * item into a {@link MeleeWeapon} — then defers the actual damage arithmetic here.
 *
 * <p>Only the pure damage-math sub-skills are wired (all deterministic — the Axe Mastery / Steel Arm
 * "checks" are {@code isNonRNGSkillActivationSuccessful}, i.e. always-on once unlocked, not a dice
 * roll). The effect-only on-hit sub-skills that mutate the target — Rupture DoT, Serrated Strikes /
 * Skull Splitter AoE, Armor Impact durability, Disarm, Counter Attack, Limit Break (PvP-only) — stay
 * deferred behind their entity/metadata/DoT adapters, matching the manager ports.
 */
public final class MeleeDamageBonus {

    /** The three melee weapon classes that carry an on-hit damage bonus (plus the no-op fallback). */
    public enum MeleeWeapon {
        SWORD,
        AXE,
        UNARMED,
        OTHER
    }

    private MeleeDamageBonus() {
    }

    /**
     * The post-armor damage after adding the attacker's melee weapon on-hit bonus. Faithful to the
     * legacy per-weapon dispatch: Swords adds Stab, Axes adds Axe Mastery, Unarmed adds Steel Arm
     * Style then Berserk (Berserk scales off the already-boosted damage). Every bonus is scaled by
     * the captured attack-cooldown charge ({@link McMMOPlayer#getAttackStrength()}), exactly as
     * legacy did. A null manager or unmet unlock/permission gate contributes nothing.
     *
     * @param mmoPlayer     the attacking player's mcMMO profile
     * @param weapon        the classification of the held main-hand item
     * @param appliedDamage the vanilla post-armor damage that would be applied
     * @return the damage mcMMO wants applied instead (>= {@code appliedDamage})
     */
    public static float applyBonus(@NotNull McMMOPlayer mmoPlayer, @NotNull MeleeWeapon weapon,
            float appliedDamage) {
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
                if (axes != null && axes.canUseAxeMastery()) {
                    boostedDamage += axes.axeMastery() * attackStrength;
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
