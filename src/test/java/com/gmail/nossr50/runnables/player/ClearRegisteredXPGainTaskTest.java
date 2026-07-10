package com.gmail.nossr50.runnables.player;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link ClearRegisteredXPGainTask} fans the diminished-returns purge across every tracked
 * player. The expiry math itself is {@link PlayerProfile}'s concern; this task's only job is the
 * fan-out over {@link UserManager}, which is what this suite verifies.
 */
class ClearRegisteredXPGainTaskTest {

    @AfterEach
    void tearDown() {
        UserManager.clearAll();
    }

    @Test
    void purgesEveryTrackedPlayer() {
        PlayerProfile profileA = mock(PlayerProfile.class);
        PlayerProfile profileB = mock(PlayerProfile.class);
        trackPlayer(profileA);
        trackPlayer(profileB);

        new ClearRegisteredXPGainTask().run();

        verify(profileA).purgeExpiredXpGains();
        verify(profileB).purgeExpiredXpGains();
    }

    private static void trackPlayer(PlayerProfile profile) {
        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        when(mmoPlayer.getProfile()).thenReturn(profile);

        UserManager.track(mmoPlayer);
    }
}
