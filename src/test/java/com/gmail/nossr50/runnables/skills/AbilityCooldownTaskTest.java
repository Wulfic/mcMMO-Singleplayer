package com.gmail.nossr50.runnables.skills;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.platform.PlatformPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Gate behaviour of the ported {@link AbilityCooldownTask}: it only announces (and flips the
 * {@code informed} flag) when the player is alive and hasn't already been informed. Chat
 * notifications are left off so the {@code NotificationManager} call is a no-op — this suite proves
 * the guards, not the routing (that's {@code NotificationManagerTest}).
 */
class AbilityCooldownTaskTest {

    private static final SuperAbilityType ABILITY = SuperAbilityType.BERSERK;

    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;

    @BeforeEach
    void setUp() {
        platformPlayer = mock(PlatformPlayer.class);
        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        when(mmoPlayer.useChatNotifications()).thenReturn(false);
    }

    @Test
    void informsWhenAliveAndNotYetInformed() {
        when(platformPlayer.isAlive()).thenReturn(true);
        when(mmoPlayer.getAbilityInformed(ABILITY)).thenReturn(false);

        new AbilityCooldownTask(mmoPlayer, ABILITY).run();

        verify(mmoPlayer).setAbilityInformed(ABILITY, true);
    }

    @Test
    void doesNothingWhenAlreadyInformed() {
        when(platformPlayer.isAlive()).thenReturn(true);
        when(mmoPlayer.getAbilityInformed(ABILITY)).thenReturn(true);

        new AbilityCooldownTask(mmoPlayer, ABILITY).run();

        verify(mmoPlayer, never()).setAbilityInformed(ABILITY, true);
    }

    @Test
    void doesNothingWhenNotAlive() {
        when(platformPlayer.isAlive()).thenReturn(false);

        new AbilityCooldownTask(mmoPlayer, ABILITY).run();

        verify(mmoPlayer, never()).setAbilityInformed(ABILITY, true);
    }
}
