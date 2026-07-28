package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.stealth.StealthManager;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stealth's <b>Smoke Bomb</b> — the two decisions in the listener that are not obvious from reading
 * it, and the config collision that would break it silently.
 */
class SmokeBombListenerTest {

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
    }

    private static McMMOPlayer playerWithAbilitySeconds(int seconds) {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.STEALTH,
                SuperAbilityType.SMOKE_BOMB)).thenReturn(seconds);
        return mmoPlayer;
    }

    private static StealthManager stealthWithFloor(int floorTicks) {
        final StealthManager stealth = mock(StealthManager.class);
        lenient().when(stealth.getSmokeBombDurationTicks()).thenReturn(floorTicks);
        return stealth;
    }

    // --- duration: both knobs are live -----------------------------------------------------------

    @Test
    void theConfiguredFloorAppliesWhileTheAbilityIsStillShort() {
        // At the level Smoke Bomb unlocks, the generic super-ability formula yields only a few
        // seconds. Without the floor the ability would be almost useless exactly when it is earned.
        assertEquals(100,
                SmokeBombListener.durationTicks(playerWithAbilitySeconds(3), stealthWithFloor(100)));
    }

    @Test
    void skillLevelOvertakesTheFloorAndThenScalesTheAbility() {
        // 8 s = 160 ticks, past the 100-tick floor — so the ability really does grow with the skill
        // rather than being pinned to a constant. Both knobs matter; neither is decorative.
        assertEquals(160,
                SmokeBombListener.durationTicks(playerWithAbilitySeconds(8), stealthWithFloor(100)));
    }

    @Test
    void aZeroedFloorLeavesTheAbilityMachineryInCharge() {
        assertEquals(200,
                SmokeBombListener.durationTicks(playerWithAbilitySeconds(10), stealthWithFloor(1)));
    }

    // --- the config collision --------------------------------------------------------------------

    @Test
    void theTwoActivesShipWithDifferentTriggerItems(@TempDir Path dataFolder) {
        // Second Wind and Smoke Bomb listen on the SAME UseItemCallback. If they ever share a
        // trigger item, one fires and the other prints its refusal message over the top — which
        // reads as a broken ability, not as a config collision, and no other test would catch it.
        final GeneralConfig config = new GeneralConfig(dataFolder);
        McMMOMod.setGeneralConfig(config);

        final String secondWind = config.getSecondWindItem();
        final String smokeBomb = config.getSmokeBombItem();

        assertTrue(secondWind != null && !secondWind.isBlank());
        assertTrue(smokeBomb != null && !smokeBomb.isBlank());
        assertNotEquals(secondWind, smokeBomb,
                "Second Wind and Smoke Bomb must not share a trigger item");
    }

    // --- the ability key the config is read under ------------------------------------------------

    @Test
    void theAbilityResolvesToItsShippedConfigKey() {
        // getCooldown/getMaxLength key off toString(), so a rename here silently falls back to the
        // int default and the shipped Cooldowns.Smoke_Bomb / Max_Seconds.Smoke_Bomb stop being read.
        assertEquals("Smoke_Bomb", SuperAbilityType.SMOKE_BOMB.toString());
    }
}
