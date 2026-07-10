package com.gmail.nossr50.runnables.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gate behaviour of the ported {@link AbilityDisableTask}: it only deactivates (and schedules the
 * follow-up cooldown reminder) when the ability is actually in active mode. Chat notifications are
 * left off so the {@code NotificationManager} calls are no-ops; the real bundled configs supply the
 * ability cooldown that sizes the scheduled reminder's delay.
 */
class AbilityDisableTaskTest {

    private static final SuperAbilityType ABILITY = SuperAbilityType.BERSERK;

    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        mmoPlayer = mock(McMMOPlayer.class);
        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        when(mmoPlayer.useChatNotifications()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.getScheduler().cancelAll();
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    @Test
    void deactivatesAndSchedulesCooldownWhenActive() {
        when(mmoPlayer.getAbilityMode(ABILITY)).thenReturn(true);
        int before = McMMOMod.getScheduler().activeCount();

        new AbilityDisableTask(mmoPlayer, ABILITY).run();

        verify(mmoPlayer).setAbilityMode(ABILITY, false);
        verify(mmoPlayer).setAbilityInformed(ABILITY, false);
        // The cooldown-refresh reminder was queued on the shared scheduler.
        assertEquals(before + 1, McMMOMod.getScheduler().activeCount());
    }

    @Test
    void doesNothingWhenNotActive() {
        when(mmoPlayer.getAbilityMode(ABILITY)).thenReturn(false);
        int before = McMMOMod.getScheduler().activeCount();

        new AbilityDisableTask(mmoPlayer, ABILITY).run();

        verify(mmoPlayer, never()).setAbilityMode(ABILITY, false);
        assertEquals(before, McMMOMod.getScheduler().activeCount());
    }
}
