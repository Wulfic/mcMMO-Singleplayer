package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.PlatformPlayer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Phase 10.1 {@link SkillManager} base delegates to its owning {@link McMMOPlayer}
 * (both mocked). Concrete skill managers arrive in Phase 10.2/10.3; this pins the base contract the
 * whole hierarchy inherits — retargeted {@code getPlayer()} return, level lookup, and XP entry.
 */
class SkillManagerTest {

    /** Minimal concrete manager, since {@link SkillManager} is abstract. */
    private static final class StubManager extends SkillManager {
        StubManager(McMMOPlayer mmoPlayer) {
            super(mmoPlayer, PrimarySkillType.MINING);
        }
    }

    @Test
    void getPlayerReturnsThePlatformPlayerFromMcMMOPlayer() {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);

        assertSame(platformPlayer, new StubManager(mmoPlayer).getPlayer());
    }

    @Test
    void getSkillLevelDelegatesForTheManagersSkill() {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getSkillLevel(PrimarySkillType.MINING)).thenReturn(42);

        assertEquals(42, new StubManager(mmoPlayer).getSkillLevel());
    }

    @Test
    void applyXpGainRoutesToBeginXpGainWithTheManagersSkill() {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        final StubManager manager = new StubManager(mmoPlayer);

        manager.applyXpGain(5f, XPGainReason.PVE, XPGainSource.SELF);

        verify(mmoPlayer).beginXpGain(PrimarySkillType.MINING, 5f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    @SuppressWarnings("removal") // intentionally exercises the deprecated single-arg overload
    void deprecatedApplyXpGainDefaultsSourceToSelf() {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        final StubManager manager = new StubManager(mmoPlayer);

        manager.applyXpGain(3f, XPGainReason.PVE);

        verify(mmoPlayer).beginXpGain(PrimarySkillType.MINING, 3f, XPGainReason.PVE,
                XPGainSource.SELF);
    }
}
