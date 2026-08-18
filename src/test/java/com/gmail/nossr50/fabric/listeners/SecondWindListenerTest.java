package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.skills.agility.Medium;
import org.junit.jupiter.api.Test;

/**
 * Second Wind's <b>duration</b>, and the one thing about it that is not obvious from reading the
 * listener: which skill it is scaled on.
 *
 * <p>Until 2026-08-17 the answer was {@code AGILITY} — the mean of Parkour, Swimming and Flying —
 * even after each body's <em>unlock</em> had been moved onto the medium's own skill. A specialist was
 * therefore still taxed by the two skills they had not trained, in the one place no gate test looks.
 *
 * <p>Every case here stubs a <b>different</b> length per skill, so the returned number names which
 * skill was asked. Stubbing them all alike would pass whatever the listener read.
 */
class SecondWindListenerTest {

    /**
     * A player whose four movement skills would each yield a distinguishable ability length.
     *
     * <p>{@code AGILITY} is stubbed too, and to a value none of the others use — it is the wrong
     * answer this test exists to catch, and leaving it unstubbed would make a regression return
     * Mockito's default 0 rather than something recognisable.
     */
    private static McMMOPlayer playerWithLengths(int parkour, int swimming, int flying, int agility) {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.PARKOUR,
                SuperAbilityType.SECOND_WIND)).thenReturn(parkour);
        lenient().when(mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.SWIMMING,
                SuperAbilityType.SECOND_WIND)).thenReturn(swimming);
        lenient().when(mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.FLYING,
                SuperAbilityType.SECOND_WIND)).thenReturn(flying);
        lenient().when(mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.AGILITY,
                SuperAbilityType.SECOND_WIND)).thenReturn(agility);
        return mmoPlayer;
    }

    @Test
    void eachMediumScalesItsDurationOnItsOwnSkill() {
        final McMMOPlayer mmoPlayer = playerWithLengths(10, 20, 30, 99);

        assertEquals(10, SecondWindListener.durationTicks(mmoPlayer, Medium.LAND),
                "sprinting on land must scale Dart on PARKOUR");
        assertEquals(20, SecondWindListener.durationTicks(mmoPlayer, Medium.WATER),
                "swimming must scale Aquaman on SWIMMING");
        assertEquals(30, SecondWindListener.durationTicks(mmoPlayer, Medium.AIR),
                "gliding must scale Limitless on FLYING");
    }

    /**
     * The specialist case the move exists for, stated as the numbers a player would see.
     *
     * <p>A swimmer with Swimming 900 and nothing in Parkour or Flying had an Agility of 300. Reading
     * the mean gave them the duration of a level-300 player; reading their own skill gives them the
     * level-900 one. If this ever returns the mean again, the assertion names the exact regression.
     */
    @Test
    void aSpecialistIsNoLongerTaxedByTheTwoSkillsTheyDidNotTrain() {
        // Lengths as the super-ability formula would yield them: 900 -> 47 s, the mean 300 -> 17 s.
        final McMMOPlayer swimmer = playerWithLengths(2, 47, 2, 17);

        assertEquals(47, SecondWindListener.durationTicks(swimmer, Medium.WATER),
                "a Swimming-900 specialist must get the Swimming-900 duration, not the 17 s their "
                        + "Agility mean of 300 used to buy");
    }

    /**
     * The mapping is total: adding a medium without giving it a skill would fail here rather than
     * silently returning 0 in play.
     */
    @Test
    void everyMediumResolvesToAMovementSkill() {
        final McMMOPlayer mmoPlayer = playerWithLengths(10, 20, 30, 99);
        for (Medium medium : Medium.values()) {
            final int ticks = SecondWindListener.durationTicks(mmoPlayer, medium);
            assertTrue(ticks == 10 || ticks == 20 || ticks == 30,
                    medium + " resolved to a skill this test did not stub (got " + ticks
                            + "); every medium must name one of PARKOUR/SWIMMING/FLYING");
        }
    }
}
