package com.gmail.nossr50.skills.unarmored;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The MC-free half of Unarmored: the damage-taken XP payout, the four Iron Skin tiers, and the
 * Thorny Skin reflect.
 *
 * <p>Rank plumbing is real ({@link RankConfig} loaded from the bundled {@code skillranks.yml})
 * rather than mocked into always-true, so the shipped tier ladder is exercised at the exact levels
 * players will meet it. That matters more here than in most skills: Iron Skin's four ranks
 * <em>are</em> its four armour tiers, so a unit test with a mocked rank would prove the table
 * lookup and nothing about the breakpoints.
 */
class UnarmoredManagerTest {

    private static final double EPSILON = 1.0E-9;

    /** The shipped XP paid per point of damage taken, restated so a retune comes through here. */
    private static final int XP_PER_DAMAGE = 100;

    private AdvancedConfig advancedConfig;
    private ExperienceConfig experienceConfig;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        advancedConfig = mock(AdvancedConfig.class);
        experienceConfig = mock(ExperienceConfig.class);
        final PlatformPlayer player = mock(PlatformPlayer.class);

        // The shipped advanced.yml values. Answered through the same (tier, default) signature the
        // manager calls with, so a test cannot accidentally assert against a table the production
        // path would never read.
        lenient().when(advancedConfig.getIronSkinArmorPoints(anyInt(), anyDouble()))
                .thenAnswer(invocation -> switch ((int) invocation.getArgument(0)) {
                    case 1 -> 7.0;
                    case 2 -> 11.0;
                    case 3 -> 15.0;
                    case 4 -> 20.0;
                    default -> 0.0;
                });
        lenient().when(advancedConfig.getThornySkinMaxBonusLevel()).thenReturn(1000);
        lenient().when(advancedConfig.getThornySkinMaxReflectDamage()).thenReturn(1.0);
        lenient().when(experienceConfig.getUnarmoredXpPerDamage()).thenReturn(XP_PER_DAMAGE);

        McMMOMod.setAdvancedConfig(advancedConfig);
        McMMOMod.setExperienceConfig(experienceConfig);
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
    }

    private UnarmoredManager managerAtLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.UNARMORED)).thenReturn(level);
        return new UnarmoredManager(mmoPlayer);
    }

    // --- the enum-prefix collision this skill's name creates -------------------------------------

    @Test
    void unarmoredSubSkillsParentOntoUnarmoredAndNotUnarmed() {
        // UNARMORED is one letter from UNARMED, and SkillTools resolves a sub-skill's parent from
        // the enum-name prefix up to the first '_'. That match is equalsIgnoreCase on the WHOLE
        // prefix rather than startsWith, so the two cannot collide — but the failure mode if it
        // ever changed is silent and total (every Unarmored sub-skill would read its ranks, its
        // advanced.yml address and its locale keys out of the Unarmed block), so it is pinned.
        assertEquals(PrimarySkillType.UNARMORED,
                SubSkillType.UNARMORED_IRON_SKIN.getParentSkill());
        assertEquals(PrimarySkillType.UNARMORED,
                SubSkillType.UNARMORED_THORNY_SKIN.getParentSkill());
        assertEquals(PrimarySkillType.UNARMED, SubSkillType.UNARMED_IRON_GRIP.getParentSkill());
        // ...and the config addresses they derive really are in different blocks.
        assertTrue(SubSkillType.UNARMORED_IRON_SKIN.getAdvConfigAddress()
                .startsWith("Skills.Unarmored."));
        assertNotEquals(SubSkillType.UNARMORED_IRON_SKIN.getAdvConfigAddress(),
                SubSkillType.UNARMED_IRON_GRIP.getAdvConfigAddress());
    }

    // --- XP --------------------------------------------------------------------------------------

    @Test
    void xpIsProportionalToDamageTaken() {
        final UnarmoredManager manager = managerAtLevel(1);
        assertEquals(100F, manager.getUnarmoredXp(1.0), EPSILON);
        assertEquals(300F, manager.getUnarmoredXp(3.0), EPSILON);
        // Fractional damage pays fractionally — a half-point hit is not rounded up into a full one.
        assertEquals(50F, manager.getUnarmoredXp(0.5), EPSILON);
    }

    @Test
    void aSingleHitIsCappedAtOneFullHealthBar() {
        // Without the clamp a charged-creeper one-shot, a /damage command or any mod dealing absurd
        // damage becomes an XP button proportional to a number the player never survives. 20 is one
        // full vanilla health bar, the same clamp Agility's Roll already applies to fall XP.
        final UnarmoredManager manager = managerAtLevel(1);
        assertEquals(20 * XP_PER_DAMAGE, manager.getUnarmoredXp(20.0), EPSILON);
        assertEquals(20 * XP_PER_DAMAGE, manager.getUnarmoredXp(1000.0), EPSILON);
        assertEquals(20 * XP_PER_DAMAGE, manager.getUnarmoredXp(Double.MAX_VALUE), EPSILON);
        // Just under the cap still scales, so the clamp is a ceiling and not a flat rate.
        assertEquals(19 * XP_PER_DAMAGE, manager.getUnarmoredXp(19.0), EPSILON);
    }

    @Test
    void aNonPositiveHitPaysNothing() {
        final UnarmoredManager manager = managerAtLevel(1);
        assertEquals(0F, manager.getUnarmoredXp(0.0), EPSILON);
        assertEquals(0F, manager.getUnarmoredXp(-5.0), EPSILON);
    }

    @Test
    void aNegativeXpModifierCannotPayNegativeXp() {
        when(experienceConfig.getUnarmoredXpPerDamage()).thenReturn(-100);
        assertEquals(0F, managerAtLevel(1).getUnarmoredXp(10.0), EPSILON);
    }

    @Test
    void onDamageTakenPaysIntoUnarmoredItself() {
        // Unarmored is standalone, not a child skill, so the gain is addressed to its own skill and
        // is not split among parents the way an Agility gain would be.
        final UnarmoredManager manager = managerAtLevel(1);
        assertEquals(300F, manager.onDamageTaken(3.0), EPSILON);
        verify(mmoPlayer).beginXpGain(PrimarySkillType.UNARMORED, 300F, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void onDamageTakenNeverPushesAZeroThroughTheXpPipeline() {
        managerAtLevel(1).onDamageTaken(0.0);
        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    // --- Iron Skin: the four tiers ---------------------------------------------------------------

    @Test
    void ironSkinIsLockedBelowItsFirstTier() {
        // skillranks.yml puts tier 1 at RetroMode 100.
        assertFalse(managerAtLevel(99).canIronSkin());
        assertEquals(0, managerAtLevel(99).getIronSkinTier());
        assertEquals(0.0, managerAtLevel(99).getSkinArmorPoints(true), EPSILON);
        assertTrue(managerAtLevel(100).canIronSkin());
    }

    @Test
    void theFourTiersLandOnTheirBreakpoints() {
        // The wiki's leather / gold / iron / diamond ladder, at the levels skillranks.yml unlocks
        // the sub-skill's four ranks. Asserted one level BELOW each breakpoint as well as on it —
        // an off-by-one in the ladder is invisible if you only ever sample the breakpoints.
        assertEquals(7.0, managerAtLevel(100).getSkinArmorPoints(true), EPSILON);
        assertEquals(7.0, managerAtLevel(199).getSkinArmorPoints(true), EPSILON);
        assertEquals(11.0, managerAtLevel(200).getSkinArmorPoints(true), EPSILON);
        assertEquals(11.0, managerAtLevel(499).getSkinArmorPoints(true), EPSILON);
        assertEquals(15.0, managerAtLevel(500).getSkinArmorPoints(true), EPSILON);
        assertEquals(15.0, managerAtLevel(999).getSkinArmorPoints(true), EPSILON);
        assertEquals(20.0, managerAtLevel(1000).getSkinArmorPoints(true), EPSILON);
    }

    @Test
    void theTierDoesNotKeepClimbingPastTheLastRank() {
        assertEquals(4, managerAtLevel(100000).getIronSkinTier());
        assertEquals(20.0, managerAtLevel(100000).getSkinArmorPoints(true), EPSILON);
    }

    @Test
    void wearingAnyArmourRemovesTheSkinEntirely() {
        // D-U1: the source wiki's "and real armour is doubled" clause is CUT. The skin is the
        // reward for going without, so it is zero at every tier the moment the player is armoured.
        // Expressed as a parameter rather than a caller-side `if` precisely so a caller cannot
        // forget it and hand a fully-plated player 20 free armour points.
        for (int level : new int[] {100, 200, 500, 1000, 100000}) {
            assertEquals(0.0, managerAtLevel(level).getSkinArmorPoints(false), EPSILON);
        }
    }

    @Test
    void aNegativeArmourPointConfigCannotBecomeAPenalty() {
        when(advancedConfig.getIronSkinArmorPoints(anyInt(), anyDouble())).thenReturn(-10.0);
        assertEquals(0.0, managerAtLevel(1000).getSkinArmorPoints(true), EPSILON);
    }

    // --- Thorny Skin -----------------------------------------------------------------------------

    @Test
    void thornySkinIsLockedBelowItsRank() {
        // skillranks.yml puts Thorny Skin at RetroMode 350.
        assertFalse(managerAtLevel(349).canThornySkin());
        assertTrue(managerAtLevel(350).canThornySkin());
        assertEquals(0.0, managerAtLevel(349).getThornsDamage(true), EPSILON);
        assertFalse(managerAtLevel(349).thornsReady(true));
    }

    @Test
    void thornsScaleToTheHalfHeartCap() {
        assertEquals(0.35, managerAtLevel(350).getThornsDamage(true), EPSILON);
        assertEquals(1.0, managerAtLevel(1000).getThornsDamage(true), EPSILON);
        // And stop there: a reflect that keeps growing turns standing still into a mob-melter.
        assertEquals(1.0, managerAtLevel(50000).getThornsDamage(true), EPSILON);
    }

    @Test
    void thornsDoNothingWhileArmoured() {
        assertEquals(0.0, managerAtLevel(1000).getThornsDamage(false), EPSILON);
        assertFalse(managerAtLevel(1000).thornsReady(false));
        assertTrue(managerAtLevel(1000).thornsReady(true));
    }

    @Test
    void aZeroedReflectConfigReportsNotReadyRatherThanReflectingNothing() {
        // thornsReady() is what the listener branches on. If it could be true while the damage is
        // zero, the listener would run its whole attacker-resolution path and deal a no-op hit —
        // which in vanilla still triggers i-frames on the attacker and eats a real hit's worth of
        // invulnerability. Keeping the two consistent in the manager stops that being a listener
        // problem.
        when(advancedConfig.getThornySkinMaxReflectDamage()).thenReturn(0.0);
        assertEquals(0.0, managerAtLevel(1000).getThornsDamage(true), EPSILON);
        assertFalse(managerAtLevel(1000).thornsReady(true));
    }

    // --- budget -----------------------------------------------------------------------------------

    @Test
    void maxingUnarmoredTakesARealAmountOfPunishment() {
        // Total XP to RetroMode 1000 on the shipped linear curve (base 1020, multiplier 20) is
        // 10N^2 + 1010N = 11,010,000. A cheap arithmetic guard that fails loudly if the modifier is
        // bumped without re-deriving what it costs the player.
        final double totalXpToMax = 10.0 * 1000 * 1000 + 1010.0 * 1000;
        final double damageToMax = totalXpToMax / XP_PER_DAMAGE;
        assertEquals(110_100.0, damageToMax, EPSILON);

        // At a sustained "lose a full 20-damage health bar every minute and heal it back" that is
        // ~92 hours. The guardrail is the same >=80h one Agility and Stealth are held to.
        final double hoursAtOneHealthBarPerMinute = damageToMax / 20.0 / 60.0;
        assertTrue(hoursAtOneHealthBarPerMinute >= 80.0,
                "Unarmored would max in " + hoursAtOneHealthBarPerMinute + "h, under the 80h floor");
    }
}
