package com.gmail.nossr50.runnables.skills;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import java.nio.file.Path;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the ported {@link SkillUnlockNotificationTask} delegates to
 * {@link com.gmail.nossr50.util.player.NotificationManager#sendPlayerUnlockNotification}: it sends
 * the unlock message when notifications are on and is a no-op when they are off.
 */
class SkillUnlockNotificationTaskTest {

    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        // The unlock message routes straight to chat (no action-bar config) and the unlock sound is a
        // no-op while SoundConfig is unbound, so only the rank lookup needs backing: RankConfig, which
        // reads GeneralConfig to pick the RetroMode vs Standard rank column.
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
    }

    @Test
    void runSendsUnlockMessageWhenNotificationsEnabled() {
        when(mmoPlayer.useChatNotifications()).thenReturn(true);
        SubSkillType subSkillType = SubSkillType.ACROBATICS_DODGE;
        Text expected = LocaleLoader.getText("JSON.SkillUnlockMessage",
                subSkillType.getLocaleName(), RankUtils.getRank(mmoPlayer, subSkillType));

        new SkillUnlockNotificationTask(mmoPlayer, subSkillType, 1).run();

        verify(platformPlayer).sendMessage(expected);
    }

    @Test
    void runIsNoOpWhenNotificationsDisabled() {
        when(mmoPlayer.useChatNotifications()).thenReturn(false);

        new SkillUnlockNotificationTask(mmoPlayer, SubSkillType.ACROBATICS_DODGE, 1).run();

        verifyNoInteractions(platformPlayer);
    }
}
