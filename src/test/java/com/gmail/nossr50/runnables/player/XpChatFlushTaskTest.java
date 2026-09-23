package com.gmail.nossr50.runnables.player;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * §81. Proves {@link XpChatFlushTask} fans the {@code /mcstats <skill> keep} flush across every
 * tracked player.
 *
 * <p>What a flush prints — and whether it prints at all — is {@link McMMOPlayer#flushXpChat()}'s
 * concern and is covered in {@code McMMOPlayerTest}. This task's only job is the fan-out over
 * {@link UserManager}, so that is all this suite asserts. Same split as
 * {@link ClearRegisteredXPGainTaskTest}.
 */
class XpChatFlushTaskTest {

    @AfterEach
    void tearDown() {
        UserManager.clearAll();
    }

    @Test
    void flushesEveryTrackedPlayer() {
        McMMOPlayer playerA = trackPlayer();
        McMMOPlayer playerB = trackPlayer();

        new XpChatFlushTask().run();

        verify(playerA).flushXpChat();
        verify(playerB).flushXpChat();
    }

    private static McMMOPlayer trackPlayer() {
        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);

        UserManager.track(mmoPlayer);
        return mmoPlayer;
    }
}
