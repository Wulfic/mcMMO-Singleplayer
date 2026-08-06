package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the MC-free K1 attacker-branch damage composition ({@link MeleeDamageBonus}) reproduces the
 * legacy {@code CombatUtils} per-weapon dispatch: each bonus is added and scaled by the attack-cooldown
 * charge, Unarmed stacks Steel Arm then Berserk (Berserk reading the already-boosted value), the Axes
 * chain runs Mastery → Impact-or-Greater-Impact → Critical Strikes in that order, and an unmet gate /
 * null manager / non-weapon contributes nothing.
 */
class MeleeDamageBonusTest {

    /** The sub-skills under test here never inspect the target beyond what their own gates stub. */
    private static final PlatformLivingEntity TARGET = mock(PlatformLivingEntity.class);

    /**
     * ⚠️ This class used to need no config at all: every bonus came from a mocked manager, so the
     * arithmetic was pure. Limit Break broke that — it reads
     * {@code AdvancedConfig#canApplyLimitBreakPVE} and the rank ladder directly rather than through a
     * manager, so {@link MeleeDamageBonus} now touches global config state and every existing test
     * NPE'd the moment it was wired. The three configs below are what the rank lookup needs
     * ({@code RankUtils.getRank} → {@code RankConfig}, {@code SkillGating} → {@code GeneralConfig}).
     *
     * <p>The players these tests mock have skill level 0, so they hold rank 0 in every Limit Break
     * and it contributes nothing — which is why the pre-existing expectations below are still exact.
     */
    @BeforeEach
    void setUpConfigs(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
    }

    @AfterEach
    void tearDownConfigs() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    private static McMMOPlayer playerWithStrength(float strength) {
        final McMMOPlayer player = mock(McMMOPlayer.class);
        when(player.getAttackStrength()).thenReturn(strength);
        return player;
    }

    @Test
    void otherWeaponAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.OTHER, 5.0f, TARGET), 1e-6,
                "a non-weapon in hand adds no bonus");
    }

    @Test
    void nullManagerAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        when(player.getSwordsManager()).thenReturn(null);
        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SWORD, 5.0f, TARGET), 1e-6,
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
        assertEquals(7.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SWORD, 5.0f, TARGET), 1e-6);
    }

    @Test
    void swordWithStabLockedAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final SwordsManager swords = mock(SwordsManager.class);
        when(swords.canUseStab()).thenReturn(false);
        when(player.getSwordsManager()).thenReturn(swords);
        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SWORD, 5.0f, TARGET), 1e-6,
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
        assertEquals(8.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.AXE, 5.0f, TARGET), 1e-6);
    }

    /**
     * Armor Impact is durability-only: it must fire without contributing damage, and it must lock out
     * Greater Impact on the same hit (legacy's {@code if (canImpact) ... else if (canGreaterImpact)}).
     */
    @Test
    void axeArmorImpactFiresWithoutDamageAndSuppressesGreaterImpact() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final AxesManager axes = mock(AxesManager.class);
        when(axes.canImpact(TARGET)).thenReturn(true);
        when(axes.canGreaterImpact(TARGET)).thenReturn(true); // would fire if it were ever reached
        when(player.getAxesManager()).thenReturn(axes);

        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.AXE, 5.0f, TARGET), 1e-6,
                "Armor Impact only wears armor, it adds no damage");
        verify(axes).impactCheck(TARGET);
        verify(axes, never()).greaterImpact(TARGET);
    }

    @Test
    void axeGreaterImpactAddsBonusScaledByAttackStrength() {
        final McMMOPlayer player = playerWithStrength(0.5f);
        final AxesManager axes = mock(AxesManager.class);
        when(axes.canImpact(TARGET)).thenReturn(false); // unarmored target
        when(axes.canGreaterImpact(TARGET)).thenReturn(true);
        when(axes.greaterImpact(TARGET)).thenReturn(2.0);
        when(player.getAxesManager()).thenReturn(axes);

        // 5 + 2 * 0.5 = 6
        assertEquals(6.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.AXE, 5.0f, TARGET), 1e-6);
        verify(axes, never()).impactCheck(TARGET);
    }

    /**
     * Critical Strikes runs last and multiplies what Axe Mastery and Greater Impact have already
     * accumulated, not the base hit — legacy passes it {@code boostedDamage}. Pinning that here stops
     * a future refactor from quietly reordering the chain and nerfing crits.
     */
    @Test
    void axeCriticalStrikesReadsTheAlreadyBoostedDamage() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final AxesManager axes = mock(AxesManager.class);
        when(axes.canUseAxeMastery()).thenReturn(true);
        when(axes.axeMastery()).thenReturn(3.0);
        when(axes.canGreaterImpact(TARGET)).thenReturn(true);
        when(axes.greaterImpact(TARGET)).thenReturn(2.0);
        when(axes.canCriticalHit(TARGET)).thenReturn(true);
        // The crit must see 5 + 3 + 2 = 10, and doubles it (PVE 2.0 → a delta of 10).
        when(axes.criticalHit(10.0)).thenReturn(10.0);
        when(player.getAxesManager()).thenReturn(axes);

        assertEquals(20.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.AXE, 5.0f, TARGET), 1e-6);
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
        assertEquals(10.5f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.UNARMED, 5.0f, TARGET), 1e-6);
    }

    @Test
    void spearAddsSpearMasteryScaledByAttackStrength() {
        final McMMOPlayer player = playerWithStrength(0.5f);
        final SpearsManager spears = mock(SpearsManager.class);
        when(spears.canUseSpearMastery()).thenReturn(true);
        when(spears.getSpearMasteryBonusDamage()).thenReturn(3.2);
        when(player.getSpearsManager()).thenReturn(spears);
        // 5 + 3.2 * 0.5 = 6.6
        assertEquals(6.6f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SPEAR, 5.0f, TARGET), 1e-6);
    }

    @Test
    void spearBelowTheMasteryUnlockAddsNothing() {
        final McMMOPlayer player = playerWithStrength(1.0f);
        final SpearsManager spears = mock(SpearsManager.class);
        when(spears.canUseSpearMastery()).thenReturn(false);
        when(player.getSpearsManager()).thenReturn(spears);

        assertEquals(5.0f, MeleeDamageBonus.applyBonus(player, MeleeWeapon.SPEAR, 5.0f, TARGET), 1e-6,
                "an unmet Spear Mastery gate contributes nothing");
        verify(spears, never()).getSpearMasteryBonusDamage();
    }

    // --- Limit Break (TODO.md item 3.1) -----------------------------------------------------------

    /**
     * A player at {@code level} in every skill. ⚠️ The rank ladder read here is the <b>RetroMode</b>
     * one — 100, 200, … 1000 — because the port ships {@code General.RetroMode.Enabled: true}. Using
     * skillranks.yml's Standard column (10, 20, …) makes Limit Break look ten times stronger than it
     * is: rank 1 arrives at level 100, not level 10.
     */
    private static McMMOPlayer playerAtLevel(float strength, int level) {
        final McMMOPlayer player = playerWithStrength(strength);
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            when(player.getSkillLevel(skill)).thenReturn(level);
        }
        return player;
    }

    /** Limit Break ships OFF, so any test that wants damage from it has to opt in, as a player does. */
    private static void enableLimitBreak(Path dataFolder) throws Exception {
        java.nio.file.Files.writeString(dataFolder.resolve("advanced.yml"),
                "Skills:\n    General:\n        LimitBreak:\n            AllowPVE: true\n");
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
    }

    @Test
    void everyMeleeWeaponCarriesItsOwnLimitBreak(@TempDir Path dataFolder) throws Exception {
        // The point of the limitBreakOf() table: all six melee classes get the bonus, and OTHER --
        // which is not a weapon -- does not. A weapon wired to the wrong constant would still pass a
        // single-weapon test, so this walks all seven.
        enableLimitBreak(dataFolder);
        final McMMOPlayer player = playerAtLevel(1.0f, 1000); // rank 10 => +10 raw
        for (MeleeWeapon weapon : MeleeWeapon.values()) {
            final float expected = weapon == MeleeWeapon.OTHER ? 5.0f : 15.0f;
            assertEquals(expected, MeleeDamageBonus.applyBonus(player, weapon, 5.0f, TARGET), 1e-6,
                    weapon + " Limit Break at rank 10");
        }
    }

    @Test
    void limitBreakScalesWithAttackStrengthAndRank(@TempDir Path dataFolder) throws Exception {
        // Melee scales by the attack-cooldown charge, exactly as every other melee bonus does (the
        // three PROJECTILE arms in EntityDamageListener deliberately do not -- see LimitBreakTest).
        enableLimitBreak(dataFolder);
        assertEquals(7.5f,
                MeleeDamageBonus.applyBonus(playerAtLevel(0.5f, 500), MeleeWeapon.SWORD, 5.0f, TARGET),
                1e-6, "rank 5 at half charge = 5 + 5 * 0.5");
        assertEquals(5.0f,
                MeleeDamageBonus.applyBonus(playerAtLevel(1.0f, 99), MeleeWeapon.SWORD, 5.0f, TARGET),
                1e-6, "below level 100 there is no rank 1 yet, so nothing is added");
    }

    @Test
    void limitBreakAddsNothingWithTheShippedDefaults() {
        // The default path, and the one almost every player is on: setUpConfigs installed the
        // BUNDLED advanced.yml, which ships AllowPVE false. A max-level player still gets nothing.
        assertEquals(5.0f,
                MeleeDamageBonus.applyBonus(playerAtLevel(1.0f, 1000), MeleeWeapon.SWORD, 5.0f,
                        TARGET),
                1e-6, "Limit Break ships off; it must contribute nothing until enabled");
    }

    @Test
    void limitBreakLandsAfterBerserkAndIsNeverMultipliedByIt(@TempDir Path dataFolder)
            throws Exception {
        // ⚠️ THE ORDERING PIN, and the reason Limit Break sits outside the switch. Berserk multiplies
        // the running total (berserkDamage reads boostedDamage), so if Limit Break were added before
        // it, Berserk would compound it. Legacy adds Limit Break last, after Berserk, in every melee
        // path -- this asserts the stub is asked about the PRE-Limit-Break damage.
        enableLimitBreak(dataFolder);
        final McMMOPlayer player = playerAtLevel(1.0f, 1000); // rank 10 => +10
        final UnarmedManager unarmed = mock(UnarmedManager.class);
        when(unarmed.canUseSteelArm()).thenReturn(true);
        when(unarmed.calculateSteelArmStyleDamage()).thenReturn(2.0);
        when(unarmed.canUseBerserk()).thenReturn(true);
        // Berserk must be handed 7.0 (= 5 + 2), NOT 17.0. An un-stubbed argument returns 0.0, so if
        // Limit Break had been applied first this call would miss and the total would collapse.
        when(unarmed.berserkDamage(7.0)).thenReturn(3.5);
        when(player.getUnarmedManager()).thenReturn(unarmed);

        // 5 + 2 + 3.5 + 10 = 20.5
        assertEquals(20.5f,
                MeleeDamageBonus.applyBonus(player, MeleeWeapon.UNARMED, 5.0f, TARGET), 1e-6);
        verify(unarmed).berserkDamage(7.0);
    }
}
