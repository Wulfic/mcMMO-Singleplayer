package com.gmail.nossr50.util.player;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.text.StringUtils;
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
        // sendPlayerUnlockNotification resolves the current rank via RankUtils, which reads RankConfig;
        // RankConfig in turn reads GeneralConfig to pick the RetroMode vs Standard rank column.
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        when(mmoPlayer.useChatNotifications()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
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

    @Test
    void unlockNotificationSendsSkillUnlockMessageToChat() {
        SubSkillType subSkillType = SubSkillType.ACROBATICS_DODGE;
        // Build the expectation from the same inputs the manager uses, so the assertion is robust to
        // the concrete rank the (unstubbed) player resolves to.
        Text expected = LocaleLoader.getText("JSON.SkillUnlockMessage",
                subSkillType.getLocaleName(), RankUtils.getRank(mmoPlayer, subSkillType));

        NotificationManager.sendPlayerUnlockNotification(mmoPlayer, subSkillType);

        verify(platformPlayer).sendMessage(expected);
        // The action bar is never used for unlock notifications (chat only, plus a sound).
        verify(platformPlayer, never()).sendActionBar(expected);
    }

    @Test
    void unlockNotificationIsNoOpWhenChatNotificationsDisabled() {
        when(mmoPlayer.useChatNotifications()).thenReturn(false);

        NotificationManager.sendPlayerUnlockNotification(mmoPlayer, SubSkillType.ACROBATICS_DODGE);

        // Gated before both the message and the unlock sound.
        verifyNoInteractions(platformPlayer);
    }

    @Test
    void unlockNotificationIsNoOpForNullPlayer() {
        // Must not throw and must not touch config/player.
        NotificationManager.sendPlayerUnlockNotification(null, SubSkillType.ACROBATICS_DODGE);
    }

    @Test
    void levelUpNotificationRoutesToActionBarAndChatCopy() {
        // advanced.yml LevelUps: Enabled=true (action bar), SendCopyOfMessageToChat=true.
        PrimarySkillType skill = PrimarySkillType.MINING;
        String skillName =
                LocaleLoader.getString("Overhaul.Name." + StringUtils.getCapitalized(skill.toString()));
        Text expected = LocaleLoader.getText("Overhaul.Levelup", skillName, 1, 2);

        NotificationManager.sendPlayerLevelUpNotification(mmoPlayer, skill, 1, 2);

        verify(platformPlayer).sendActionBar(expected);
        verify(platformPlayer).sendMessage(expected);
    }

    @Test
    void levelUpNotificationIsNoOpWhenChatNotificationsDisabled() {
        when(mmoPlayer.useChatNotifications()).thenReturn(false);

        NotificationManager.sendPlayerLevelUpNotification(mmoPlayer, PrimarySkillType.MINING, 1, 2);

        verifyNoInteractions(platformPlayer);
    }

    @Test
    void levelUpNotificationIsNoOpForNullPlayer() {
        // Must not throw and must not touch config/player.
        NotificationManager.sendPlayerLevelUpNotification(null, PrimarySkillType.MINING, 1, 2);
    }
}
