package com.gmail.nossr50.skills.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import org.mockito.ArgumentMatchers;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Taming numeric cores (Phase 10.3) against the real bundled configs.
 *
 * <p>RetroMode is on by default (skillranks.yml), so Taming sub-skills unlock at these Taming levels:
 * BeastLore 1, CallOfTheWild 1, EnvironmentallyAware 100, Gore 150, Pummel/FastFoodService 200,
 * ThickFur 250, HolyHound 350, ShockProof 500, SharpenedClaws 750. advanced.yml default modifiers:
 * Gore 2.0, SharpenedClaws bonus 2.0, ThickFur 2.0, ShockProof 6.0.
 */
class TamingManagerTest {

    private McMMOPlayer mmoPlayer;
    private TamingManager tamingManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        tamingManager = new TamingManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atTamingLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.TAMING)).thenReturn(level);
    }

    @Test
    void gatesUnlockAtTheirRetroModeLevels() {
        atTamingLevel(0);
        assertFalse(tamingManager.canUseGore(), "Gore not yet unlocked at level 0");
        assertFalse(tamingManager.canUseThickFur(), "Thick Fur not yet unlocked at level 0");
        assertFalse(tamingManager.canUseSharpenedClaws(), "Sharpened Claws not yet at level 0");

        atTamingLevel(150);
        assertTrue(tamingManager.canUseGore(), "Gore unlocks at 150 (RetroMode)");
        assertFalse(tamingManager.canUseThickFur(), "Thick Fur still locked (unlocks at 250)");

        atTamingLevel(250);
        assertTrue(tamingManager.canUseThickFur(), "Thick Fur unlocks at 250");

        atTamingLevel(750);
        assertTrue(tamingManager.canUseSharpenedClaws(), "Sharpened Claws unlocks at 750");
    }

    @Test
    void holyHoundUnlocksOffEnvironmentallyAwareRankLadder() {
        atTamingLevel(99);
        assertFalse(tamingManager.canUseHolyHound(), "Environmentally Aware ladder unlocks at 100");
        atTamingLevel(100);
        assertTrue(tamingManager.canUseHolyHound(),
                "Holy Hound is gated on the Environmentally Aware unlock level (legacy quirk)");
        // proves it does NOT wait for the Holy Hound (350) ladder
    }

    @Test
    void goreReturnsOnlyTheExtraDamage() {
        // default Gore modifier 2.0 -> (10 * 2) - 10 = 10 extra damage
        assertEquals(10.0, tamingManager.gore(10.0), 1.0e-9);
        assertEquals(0.0, tamingManager.gore(0.0), 1.0e-9);
    }

    @Test
    void sharpenedClawsReturnsConfiguredBonus() {
        assertEquals(2.0, tamingManager.sharpenedClaws(), 1.0e-9);
    }

    @Test
    void thickFurAndShockProofDivideByTheirModifiers() {
        assertEquals(5.0, tamingManager.processThickFur(10.0), 1.0e-9, "Thick Fur modifier 2.0");
        assertEquals(2.0, tamingManager.processShockProof(12.0), 1.0e-9, "Shock Proof modifier 6.0");
    }

    @Test
    void awardTamingXpLooksUpPerEntityXpAndGainsIt() {
        // experience.yml default: Taming.Animal_Taming.Wolf = 250.
        tamingManager.awardTamingXP("Wolf");
        verify(mmoPlayer).beginXpGain(PrimarySkillType.TAMING, 250f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void awardTamingXpIsNoOpForEntityWithNoConfiguredXp() {
        // An entity type with no Taming.Animal_Taming entry resolves to 0 XP -> no gain.
        tamingManager.awardTamingXP("Not_A_Real_Animal");
        verify(mmoPlayer, never()).beginXpGain(ArgumentMatchers.any(), ArgumentMatchers.anyFloat(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void beastLoreHorseJumpStrengthMatchesTheWikiPolynomial() {
        // raw jump-strength attribute of 0.7 -> ~2.8917 (verbatim wiki polynomial)
        assertEquals(2.8917, TamingManager.beastLoreHorseJumpStrength(0.7), 1.0e-3);
        // a raw value of 0 -> the polynomial's constant term
        assertEquals(-0.343930367, TamingManager.beastLoreHorseJumpStrength(0.0), 1.0e-9);
    }
}
