package com.gmail.nossr50.util.player;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the singleplayer {@link NotificationManager} routing against the real bundled
 * {@code advanced.yml} {@code Feedback.ActionBarNotifications} section:
 * <ul>
 *   <li>{@code AbilityCoolDown}: Enabled=true, copy-to-chat=false → action bar only.</li>
 *   <li>{@code SubSkillUnlocked}: Enabled=true, copy-to-chat=true → action bar + chat copy.</li>
 *   <li>{@code SubSkillFailed}: Enabled=false → system chat only.</li>
 * </ul>
 * plus the {@code useChatNotifications()} no-op gate.
 */
class NotificationManagerTest {

    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        when(mmoPlayer.useChatNotifications()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
    }

    @Test
    void actionBarTypeWithoutCopyGoesToActionBarOnly() {
        Text expected = LocaleLoader.getText("Skills.TooTired", "5");

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_COOLDOWN,
                "Skills.TooTired", "5");

        verify(platformPlayer).sendActionBar(expected);
        verify(platformPlayer, never()).sendMessage(expected);
    }

    @Test
    void actionBarTypeWithCopyAlsoGoesToChat() {
        Text expected = LocaleLoader.getText("Skills.TooTired", "3");

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_UNLOCKED,
                "Skills.TooTired", "3");

        verify(platformPlayer).sendActionBar(expected);
        verify(platformPlayer).sendMessage(expected);
    }

    @Test
    void systemTypeGoesToChatOnly() {
        Text expected = LocaleLoader.getText("Skills.TooTired", "9");

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE_FAILED,
                "Skills.TooTired", "9");

        verify(platformPlayer).sendMessage(expected);
        verify(platformPlayer, never()).sendActionBar(expected);
    }

    @Test
    void noNotificationsWhenChatNotificationsDisabled() {
        when(mmoPlayer.useChatNotifications()).thenReturn(false);

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_COOLDOWN,
                "Skills.TooTired", "5");

        verifyNoInteractions(platformPlayer);
    }

    @Test
    void nullPlayerIsANoOp() {
        // Must not throw and must not read config/player.
        NotificationManager.sendPlayerInformation(null, NotificationType.ABILITY_COOLDOWN,
                "Skills.TooTired", "5");
    }

    @Test
    void chatOnlyBypassesActionBarRouting() {
        Text expected = LocaleLoader.getText("Skills.TooTired", "2");

        NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, "Skills.TooTired", "2");

        verify(platformPlayer).sendMessage(expected);
        verify(platformPlayer, never()).sendActionBar(expected);
    }
}
