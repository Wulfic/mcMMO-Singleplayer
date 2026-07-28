package com.gmail.nossr50.skills.agility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parkour's <b>Snow Walker</b> — and specifically the one thing about it that is easy to get wrong.
 *
 * <p>Every other movement sub-skill hangs off {@code AGILITY}, whose level is the <em>mean</em> of
 * Parkour, Swimming and Flying. Snow Walker deliberately does not: it is parented to {@code PARKOUR}
 * so it is earned by running rather than handed over by a strong swimmer dragging the average up.
 * That distinction lives entirely in the enum constant's name prefix, so it would survive no review
 * and produce no error if it were renamed — hence these tests.
 */
class ParkourSnowWalkerTest {

    private PlatformPlayer player;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        player = mock(PlatformPlayer.class);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
    }

    @Test
    void theSubSkillIsParentedToParkourNotAgility() {
        // The parent map keys off the enum name's prefix. Renaming the constant to AGILITY_* would
        // silently re-gate the whole sub-skill onto the three-skill average.
        assertSame(PrimarySkillType.PARKOUR, SubSkillType.PARKOUR_SNOW_WALKER.getParentSkill());
    }

    @Test
    void itUnlocksAtParkourOneHundredInRetroMode() {
        assertEquals(100,
                RankUtils.getRankUnlockLevel(SubSkillType.PARKOUR_SNOW_WALKER, 1));
    }

    @Test
    void aNoviceCannotWalkOnSnow() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(99);

        assertFalse(new AgilityManager(mmoPlayer).canSnowWalk());
    }

    @Test
    void oneHundredParkourUnlocksIt() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(100);

        assertTrue(new AgilityManager(mmoPlayer).canSnowWalk());
    }

    @Test
    void aHighAgilityAverageDoesNotGrantIt() {
        // The whole point of the Parkour parenting: someone who maxed Swimming and Flying has a high
        // Agility level, and must still be earning Snow Walker on their own two feet.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(0);
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.AGILITY)).thenReturn(667);

        assertFalse(new AgilityManager(mmoPlayer).canSnowWalk());
    }
}
