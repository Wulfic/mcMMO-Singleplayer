package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import org.junit.jupiter.api.Test;

/**
 * Proves the MC-free K1 attacker-branch damage composition ({@link MeleeDamageBonus}) reproduces the
 * legacy {@code CombatUtils} per-weapon dispatch: each bonus is added and scaled by the attack-cooldown
 * charge, Unarmed stacks Steel Arm then Berserk (Berserk reading the already-boosted value), and an
 * unmet gate / null manager / non-weapon contributes nothing.
 */
class MeleeDamageBonusTest {

    private static McMMOPlayer playerWithStrength(float strength) {
        final McMMOPlayer player = mock(McMMOPlayer.class);
        when(player.getAttackStrength()).thenReturn(strength);
        return player;
    }

    @Test
    void otherWeaponAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.OTHER, 5.0f), 1e-6,
                "a non-weapon in hand adds no bonus");
    }

    @Test
    void nullManagerAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        when(player.getSwordsManager()).thenReturn(null);
        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SWORD, 5.0f), 1e-6,
                "a missing manager (data not loaded) adds no bonus");
    }

    @Test
    void swordAddsStabScaledByAttackStrength() {
        final McMMOPlayer player = playerWithStrength(0.5f);
        final SwordsManager swords = mock(SwordsManager.class);
        when(swords.canUseStab()).thenReturn(true);
        when(swords.getStabDamage()).thenReturn(4.0);
        when(player.getSwordsManager()).thenReturn(swords);
        // 5 + 4 * 0.5 = 7
        assertEquals(7.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SWORD, 5.0f), 1e-6);
    }

    @Test
    void swordWithStabLockedAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final SwordsManager swords = mock(SwordsManager.class);
        when(swords.canUseStab()).thenReturn(false);
        when(player.getSwordsManager()).thenReturn(swords);
        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SWORD, 5.0f), 1e-6,
                "locked Stab adds no bonus");
    }

    @Test
    void axeAddsMasteryScaledByAttackStrength() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final AxesManager axes = mock(AxesManager.class);
        when(axes.canUseAxeMastery()).thenReturn(true);
        when(axes.axeMastery()).thenReturn(3.0);
        when(player.getAxesManager()).thenReturn(axes);
        // 5 + 3 * 1 = 8
        assertEquals(8.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.AXE, 5.0f), 1e-6);
    }

    @Test
    void unarmedStacksSteelArmThenBerserk() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final UnarmedManager unarmed = mock(UnarmedManager.class);
        when(unarmed.canUseSteelArm()).thenReturn(true);
        when(unarmed.calculateSteelArmStyleDamage()).thenReturn(2.0);
        when(unarmed.canUseBerserk()).thenReturn(true);
        // Berserk reads the damage already boosted by Steel Arm (5 + 2 = 7).
        when(unarmed.berserkDamage(7.0)).thenReturn(3.5);
        when(player.getUnarmedManager()).thenReturn(unarmed);
        // 5 + 2 * 1 + 3.5 * 1 = 10.5
        assertEquals(10.5f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.UNARMED, 5.0f), 1e-6);
    }
}
