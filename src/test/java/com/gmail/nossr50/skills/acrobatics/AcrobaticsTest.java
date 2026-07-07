package com.gmail.nossr50.skills.acrobatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the two provable Acrobatics fragments that survive the Phase 10.3 port:
 * <ul>
 *   <li>{@link Acrobatics#calculateModifiedDodgeDamage(double, double)} — pure dodge-damage math,
 *       floored at 1.0;</li>
 *   <li>{@link AcrobaticsManager#canGainRollXP()} — the anti-exploit cooldown that throttles Roll XP
 *       farming.</li>
 * </ul>
 *
 * <p>{@link ExperienceConfig} is mocked here (rather than loaded from the bundled yml) so the
 * exploit-prevention toggle can be flipped both ways — the bundled default only exercises the
 * "prevention on" branch.
 */
class AcrobaticsTest {

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
    }

    @Test
    void modifiedDodgeDamageHalvesAndFloors() {
        assertEquals(5.0D, Acrobatics.calculateModifiedDodgeDamage(10.0D, 2.0D), 1e-9,
                "10 / 2 = 5");
        assertEquals(25.0D, Acrobatics.calculateModifiedDodgeDamage(100.0D, 4.0D), 1e-9,
                "100 / 4 = 25");
        assertEquals(1.0D, Acrobatics.calculateModifiedDodgeDamage(1.0D, 2.0D), 1e-9,
                "0.5 floored to 1.0 — a dodge never fully negates a hit");
    }

    @Test
    void rollXpAlwaysAllowedWhenExploitPreventionOff() {
        final ExperienceConfig experienceConfig = mock(ExperienceConfig.class);
        when(experienceConfig.isAcrobaticsExploitingPrevented()).thenReturn(false);
        McMMOMod.setExperienceConfig(experienceConfig);

        final AcrobaticsManager manager = new AcrobaticsManager(mock(McMMOPlayer.class));

        for (int i = 0; i < 10; i++) {
            assertTrue(manager.canGainRollXP(), "no cooldown when exploit prevention is off");
        }
    }

    @Test
    void rollXpCooldownBlocksRapidRetriesWhenPreventionOn() {
        final ExperienceConfig experienceConfig = mock(ExperienceConfig.class);
        when(experienceConfig.isAcrobaticsExploitingPrevented()).thenReturn(true);
        McMMOMod.setExperienceConfig(experienceConfig);

        final AcrobaticsManager manager = new AcrobaticsManager(mock(McMMOPlayer.class));

        assertTrue(manager.canGainRollXP(), "first roll is allowed and arms the cooldown");
        assertFalse(manager.canGainRollXP(), "immediate retry is inside the cooldown → blocked");
        assertFalse(manager.canGainRollXP(), "still blocked, and the penalty keeps lengthening");
    }
}
