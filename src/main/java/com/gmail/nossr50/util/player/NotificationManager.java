package com.gmail.nossr50.util.player;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.McMMOMessageType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sends player-facing feedback (level-up / super-ability / tool-ready / cooldown messages) to the
 * action bar or chat. This is the singleplayer port of legacy {@code NotificationManager}.
 *
 * <p><b>What was dropped and why (singleplayer):</b>
 * <ul>
 *   <li><b>The cancellable {@code McMMOPlayerNotificationEvent}</b> — its sole purpose was to let
 *       <em>other Bukkit plugins</em> veto/rewrite a notification. There is no external plugin loop
 *       in a singleplayer Fabric mod, so the event (and the whole Adventure {@code Audience}/
 *       {@code TextComponentFactory} path it fed) is cut; messages route straight to the player.</li>
 *   <li><b>Level-up / power-level broadcasts, admin notifications, sensitive-command notifications,
 *       {@code broadcastTitle}, "nearby players" fan-out</b> — all multiplayer/server-admin surfaces
 *       (Phase 1.5 scope cut). "Nearby players" collapses to "the player" in singleplayer.</li>
 * </ul>
 *
 * <p>Message text is built via {@link LocaleLoader#getText} (the ported {@code §}-string → vanilla
 * {@link Text} parser), and routing (action bar vs. chat, plus the optional chat copy) is read from
 * the real {@code advanced.yml} {@code Feedback.ActionBarNotifications} section via
 * {@link McMMOMod#getAdvancedConfig()} — verbatim with legacy. All entry points take an
 * {@link McMMOPlayer} (the trigger/skill call sites already hold it) and are no-ops when the player
 * has chat notifications toggled off, matching legacy's {@code useChatNotifications()} gate.
 *
 * <p>Still deferred (need unported adapters): {@code sendPlayerLevelUpNotification} (special
 * level-up component formatting via the dropped Adventure {@code TextComponentFactory}).
 */
public final class NotificationManager {

    private NotificationManager() {
    }

    /**
     * Sends {@code player} a notification built from a locale {@code key} (with optional
     * substitution {@code values}). Routed to the action bar or system chat per the notification
     * type's {@code advanced.yml} setting; when action-bar routing is configured to also copy to
     * chat, both are sent. No-op if the player is {@code null} or has chat notifications disabled.
     *
     * @param mmoPlayer target player (may be {@code null}; treated as a no-op)
     * @param notificationType the notification's routing category
     * @param key the locale key for the message body
     * @param values values substituted into the localized message, in order
     */
    public static void sendPlayerInformation(@Nullable McMMOPlayer mmoPlayer,
            @NotNull NotificationType notificationType, @NotNull String key,
            @NotNull String... values) {
        if (mmoPlayer == null || !mmoPlayer.useChatNotifications()) {
            return;
        }

        McMMOMessageType destination =
                McMMOMod.getAdvancedConfig().doesNotificationUseActionBar(notificationType)
                        ? McMMOMessageType.ACTION_BAR : McMMOMessageType.SYSTEM;

        Text message = LocaleLoader.getText(key, (Object[]) values);

        PlatformPlayer player = mmoPlayer.getPlayer();
        if (destination == McMMOMessageType.ACTION_BAR) {
            player.sendActionBar(message);

            // The action-bar message can also be mirrored into the chat feed, per config.
            if (McMMOMod.getAdvancedConfig().doesNotificationSendCopyToChat(notificationType)) {
                player.sendMessage(message);
            }
        } else {
            player.sendMessage(message);
        }
    }

    /**
     * Whether {@code mmoPlayer} currently receives mcMMO notifications (chat-notifications toggle).
     */
    public static boolean doesPlayerUseNotifications(@Nullable McMMOPlayer mmoPlayer) {
        return mmoPlayer != null && mmoPlayer.useChatNotifications();
    }

    /**
     * Sends a message straight to chat (never the action bar), bypassing the per-type routing.
     * No-op if the player is {@code null} or has chat notifications disabled.
     */
    public static void sendPlayerInformationChatOnly(@Nullable McMMOPlayer mmoPlayer,
            @NotNull String key, @NotNull String... values) {
        if (mmoPlayer == null || !mmoPlayer.useChatNotifications()) {
            return;
        }

        mmoPlayer.getPlayer().sendMessage(LocaleLoader.getText(key, (Object[]) values));
    }

    /**
     * As {@link #sendPlayerInformationChatOnly} but wraps the resolved message in the mcMMO prefix
     * template ({@code mcMMO.Template.Prefix}).
     */
    public static void sendPlayerInformationChatOnlyPrefixed(@Nullable McMMOPlayer mmoPlayer,
            @NotNull String key, @NotNull String... values) {
        if (mmoPlayer == null || !mmoPlayer.useChatNotifications()) {
            return;
        }

        String preColored = LocaleLoader.getString(key, (Object[]) values);
        mmoPlayer.getPlayer().sendMessage(LocaleLoader.getText("mcMMO.Template.Prefix", preColored));
    }

    /**
     * Tells {@code mmoPlayer} they just unlocked a new rank of {@code subSkillType}: a chat message
     * built from the {@code JSON.SkillUnlockMessage} locale key (skill name + current rank) plus the
     * {@link SoundType#SKILL_UNLOCKED} sound. No-op if the player is {@code null} or has chat
     * notifications disabled (matching legacy's gate on both the message and the sound).
     *
     * <p>Singleplayer port of legacy {@code sendPlayerUnlockNotification}. Legacy attached an
     * Adventure hover (subskill description) and a click-to-run-command to the message via the
     * dropped {@code TextComponentFactory}; those interactive extras are cosmetic and are dropped
     * here, same as this port does for the level-up notification — the unlock text and sound are
     * preserved.
     *
     * @param mmoPlayer target player (may be {@code null}; treated as a no-op)
     * @param subSkillType the subskill whose new rank was just unlocked
     */
    public static void sendPlayerUnlockNotification(@Nullable McMMOPlayer mmoPlayer,
            @NotNull SubSkillType subSkillType) {
        if (mmoPlayer == null || !mmoPlayer.useChatNotifications()) {
            return;
        }

        Text message = LocaleLoader.getText("JSON.SkillUnlockMessage",
                subSkillType.getLocaleName(), RankUtils.getRank(mmoPlayer, subSkillType));
        mmoPlayer.getPlayer().sendMessage(message);

        SoundManager.sendCategorizedSound(mmoPlayer.getPlayer(), SoundType.SKILL_UNLOCKED,
                SoundCategory.MASTER);
    }
}
